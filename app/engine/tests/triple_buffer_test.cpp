// The three-slot snapshot buffer that carries the grid and the click set to the audio callback (research R2).
#include <catch2/catch_test_macros.hpp>

#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <thread>

#include "metronome/triple_buffer.h"

namespace {

using Payload = std::array<uint32_t, 64>;

Payload filled(uint32_t value) {
    Payload payload{};
    payload.fill(value);
    return payload;
}

}  // namespace

TEST_CASE("latest before any write is the default-constructed value", "[triplebuffer]") {
    tembra::TripleBuffer<Payload> buffer;
    const Payload& value = buffer.latest();
    for (uint32_t element : value) REQUIRE(element == 0u);
}

TEST_CASE("a single-threaded write is visible to the next latest", "[triplebuffer]") {
    tembra::TripleBuffer<Payload> buffer;
    buffer.write(filled(7));
    REQUIRE(buffer.latest()[0] == 7u);
    REQUIRE(buffer.latest()[63] == 7u);  // no new write: the same slot again
    buffer.write(filled(8));
    buffer.write(filled(9));
    REQUIRE(buffer.latest()[10] == 9u);  // only the newest survives
}

TEST_CASE("the reader never sees a torn write and the writer never blocks", "[triplebuffer]") {
    tembra::TripleBuffer<Payload> buffer;
    constexpr uint32_t kWrites = 100000;
    std::atomic<bool> done{false};
    std::atomic<uint64_t> torn{0};
    std::atomic<uint64_t> reads{0};
    std::atomic<uint64_t> backwards{0};

    std::thread reader([&] {
        uint32_t previous = 0;
        while (!done.load(std::memory_order_acquire)) {
            const Payload& value = buffer.latest();
            const uint32_t first = value[0];
            for (uint32_t element : value) {
                if (element != first) torn.fetch_add(1);
            }
            if (first < previous) backwards.fetch_add(1);
            previous = first;
            reads.fetch_add(1);
        }
    });

    const auto begin = std::chrono::steady_clock::now();
    for (uint32_t i = 1; i <= kWrites; ++i) buffer.write(filled(i));
    const auto elapsed = std::chrono::steady_clock::now() - begin;
    done.store(true, std::memory_order_release);
    reader.join();

    REQUIRE(torn.load() == 0u);
    REQUIRE(backwards.load() == 0u);
    REQUIRE(reads.load() > 0u);
    REQUIRE(std::chrono::duration_cast<std::chrono::milliseconds>(elapsed).count() < 1000);
    const Payload& last = buffer.latest();
    for (uint32_t element : last) REQUIRE(element == kWrites);
}
