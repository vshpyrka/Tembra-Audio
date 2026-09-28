package audio.tembra.ui.designsystem

/**
 * How a [TembraSegmentedControl] shares its width: [Content] hugs every label (the selector rows), [Equal] gives each
 * segment the same share of the width its caller sets (the landscape settings column, the design's `grid-cols-2`).
 */
enum class SegmentWidths { Content, Equal }
