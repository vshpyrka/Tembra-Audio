package audio.tembra.ui.drop

/** A drop's [plan] and the items it leaves out ([skipped]), for one message (feature 011, spec FR-026). */
data class DropOutcome(val plan: DropPlan, val skipped: List<SkippedItem>)
