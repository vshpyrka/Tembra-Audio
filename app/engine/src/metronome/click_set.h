// One prepared sound: its three levels (research R3; contracts/click-sounds.md).
#ifndef TEMBRA_METRONOME_CLICK_SET_H
#define TEMBRA_METRONOME_CLICK_SET_H

#include "metronome/click_sample.h"

namespace tembra {

/** Sub (−17 dBFS), Click (−9 dBFS) and Accent (−3 dBFS) of one sound at the stream rate. */
struct ClickSet {
    ClickSample sub;
    ClickSample click;
    ClickSample accent;
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_CLICK_SET_H
