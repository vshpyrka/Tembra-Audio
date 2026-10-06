#include "tuner/level_gate.h"

namespace tembra {

bool LevelGate::update(float gateDb, float levelDb) {
    const bool changed = gateDb != seenGateDb_;
    seenGateDb_ = gateDb;
    if (isOff(gateDb)) {
        open_ = true;
    } else if (changed || !open_) {
        open_ = levelDb >= gateDb;
    } else {
        open_ = levelDb >= gateDb - kCloseMarginDb;
    }
    return open_;
}

}  // namespace tembra
