package audio.tembra.ui.designsystem

/**
 * Where [TembraGradientCard] draws its two blurred corner discs (research R10 of feature 004): the `primaryContainer`
 * disc and the `secondaryContainer` disc on opposite corners. [TopStartBottomEnd] is the tuner's placement (primary
 * top-start, secondary bottom-end); [TopEndBottomStart] mirrors it for the tablet dial card (secondary top-end,
 * primary bottom-start); [None] draws neither.
 */
enum class CardDiscs { TopStartBottomEnd, TopEndBottomStart, None }
