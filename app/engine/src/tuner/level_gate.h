// The noise gate's rule (feature 006, research R3; contracts/engine-c-abi-v4.md): sound is read once its level reaches
// the gate, and from then on until it has fallen a margin under it, so a fading note is not cut short and a note at the
// gate level does not flicker. A changed gate value is judged afresh, without the margin, so moving the handle past a
// steady sound closes the gate at once. One instance per analyzer, used by the analysis thread only; no allocation,
// no locks.
#ifndef TEMBRA_TUNER_LEVEL_GATE_H
#define TEMBRA_TUNER_LEVEL_GATE_H

namespace tembra {

class LevelGate {
public:
    /** The lowest level the engine reports, in dB below full scale. */
    static constexpr float kFloorDb = -120.0f;

    /** Any gate value under the floor means "no gate"; this is the one the engine stores for it. */
    static constexpr float kOff = -1000.0f;

    /** How far under the gate level an open gate closes (research R3; User Story 2). */
    static constexpr float kCloseMarginDb = 3.0f;

    static bool isOff(float gateDb) { return gateDb < kFloorDb; }

    /** One hop: [gateDb] is the opening level or [kOff], [levelDb] the input level. Returns whether sound is read. */
    bool update(float gateDb, float levelDb);

    bool open() const { return open_; }

private:
    bool open_ = true;
    float seenGateDb_ = kOff;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_LEVEL_GATE_H
