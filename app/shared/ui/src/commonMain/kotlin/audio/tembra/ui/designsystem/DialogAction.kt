package audio.tembra.ui.designsystem

/** One button of a [TembraDialog]: its text, what it does, and the test tag the journeys and tests reach it by. */
data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val testTag: String? = null,
)
