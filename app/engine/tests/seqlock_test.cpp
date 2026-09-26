// The seqlock that carries one tembra_pitch_reading from the analysis thread to the UI thread (research R4).
#include <catch2/catch_test_macros.hpp>

#include <atomic>
#include <cstdint>
#include <thread>

#include "tembra_engine.h"
#include "tuner/seqlock.h"

TEST_CASE("a never-written seqlock loads the zero-initialised value", "[seqlock]") {
    tembra::Seqlock<tembra_pitch_reading> lock;
    const tembra_pitch_reading reading = lock.load();
    REQUIRE(reading.sequence == 0);
    REQUIRE(reading.frequency_hz == 0.0f);
    REQUIRE(reading.has_pitch == 0);
    REQUIRE(reading.synthetic == 0);
}

TEST_CASE("store then load round-trips a reading", "[seqlock]") {
    tembra::Seqlock<tembra_pitch_reading> lock;
    tembra_pitch_reading in{};
    in.sequence = 7;
    in.frequency_hz = 82.41f;
    in.clarity = 0.93f;
    in.has_pitch = 1;
    lock.store(in);
    const tembra_pitch_reading out = lock.load();
    REQUIRE(out.sequence == 7);
    REQUIRE(out.frequency_hz == 82.41f);
    REQUIRE(out.clarity == 0.93f);
    REQUIRE(out.has_pitch == 1);
}

TEST_CASE("concurrent loads are always self-consistent", "[seqlock][threads]") {
    tembra::Seqlock<tembra_pitch_reading> lock;
    constexpr uint64_t kWrites = 100000;
    std::atomic<bool> done{false};
    std::atomic<uint64_t> inconsistent{0};
    std::atomic<uint64_t> loads{0};

    std::thread writer([&] {
        for (uint64_t sequence = 1; sequence <= kWrites; ++sequence) {
            tembra_pitch_reading reading{};
            reading.sequence = sequence;
            reading.frequency_hz = static_cast<float>(sequence);
            reading.clarity = static_cast<float>(sequence) / 2.0f;
            reading.has_pitch = static_cast<uint8_t>(sequence & 1u);
            lock.store(reading);
        }
        done.store(true, std::memory_order_release);
    });
    std::thread reader([&] {
        while (!done.load(std::memory_order_acquire)) {
            const tembra_pitch_reading r = lock.load();
            loads.fetch_add(1, std::memory_order_relaxed);
            if (r.sequence == 0) continue;
            const bool consistent = r.frequency_hz == static_cast<float>(r.sequence) &&
                                    r.clarity == static_cast<float>(r.sequence) / 2.0f &&
                                    r.has_pitch == static_cast<uint8_t>(r.sequence & 1u);
            if (!consistent) inconsistent.fetch_add(1, std::memory_order_relaxed);
        }
    });
    writer.join();
    reader.join();
    REQUIRE(inconsistent.load() == 0);
    REQUIRE(loads.load() > 0);
    const tembra_pitch_reading last = lock.load();
    REQUIRE(last.sequence == kWrites);
}
