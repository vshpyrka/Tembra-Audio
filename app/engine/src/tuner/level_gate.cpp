#include "tuner/level_gate.h"

namespace tembra {

bool LevelGate::update(float gateDb, float levelDb) {
    open_ = isOff(gateDb) || levelDb >= gateDb;
    return open_;
}

}  // namespace tembra
