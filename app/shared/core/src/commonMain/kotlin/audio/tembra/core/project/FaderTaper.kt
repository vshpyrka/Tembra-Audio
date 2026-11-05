package audio.tembra.core.project

/**
 * The fader's console taper (feature 012, spec FR-003; research R3): the mapping between the handle's position along
 * the throw (0 at the bottom, 1 at the top) and the gain in decibels, piecewise linear in dB between the knots
 * 1.0 → +6, 0.7 → 0, 0.5 → −12, 0.2 → −40, 0.02 → −60 and silence below, so the range where mixing happens is the
 * finest. Exact at the knots and invertible, shared by the control's drag and its semantics (which work in dB).
 */
object FaderTaper {
    // The knots from the top of the throw down; the dB column uses the same index. Declared first: an object's
    // initialisers run in order and `ticks` below reads them.
    private val KNOT_THROWS = floatArrayOf(1.0f, 0.7f, 0.5f, 0.2f, 0.02f)
    private val KNOT_DBS = floatArrayOf(6f, 0f, -12f, -40f, -60f)
    private const val SILENCE_BELOW = 0.02f

    /** The gain for a throw position; silence below the last knot, the maximum above the top. */
    fun dbOf(throwPosition: Float): GainDb {
        if (throwPosition < SILENCE_BELOW) return GainDb.Silence
        if (throwPosition >= KNOT_THROWS.first()) return GainDb(KNOT_DBS.first())
        for (i in 1 until KNOT_THROWS.size) {
            val upperThrow = KNOT_THROWS[i - 1]
            val lowerThrow = KNOT_THROWS[i]
            if (throwPosition >= lowerThrow) {
                if (throwPosition == lowerThrow) return GainDb(KNOT_DBS[i])
                val fraction = (throwPosition - lowerThrow) / (upperThrow - lowerThrow)
                return GainDb(KNOT_DBS[i] + fraction * (KNOT_DBS[i - 1] - KNOT_DBS[i]))
            }
        }
        return GainDb(KNOT_DBS.last())
    }

    /** The throw position for a gain: 0 for silence, the bottom knot for anything under −60 dB, the top for +6 dB. */
    fun throwOf(gain: GainDb): Float {
        if (gain == GainDb.Silence) return 0f
        val db = gain.value
        if (db >= KNOT_DBS.first()) return KNOT_THROWS.first()
        for (i in 1 until KNOT_DBS.size) {
            val upperDb = KNOT_DBS[i - 1]
            val lowerDb = KNOT_DBS[i]
            if (db >= lowerDb) {
                if (db == lowerDb) return KNOT_THROWS[i]
                val fraction = (db - lowerDb) / (upperDb - lowerDb)
                return KNOT_THROWS[i] + fraction * (KNOT_THROWS[i - 1] - KNOT_THROWS[i])
            }
        }
        return KNOT_THROWS.last()
    }

    /** The scale's labelled marks from the top down, at their throw positions; "∞" is the bottom of the throw. */
    val ticks: List<FaderTick> = listOf(6f, 3f, 0f, -3f, -6f, -10f, -20f, -30f, -40f, -60f).map { db ->
        FaderTick(throwOf(GainDb(db)), labelOf(db))
    } + FaderTick(0f, "∞")

    private fun labelOf(db: Float): String {
        val whole = db.toInt()
        return when {
            whole > 0 -> "+$whole"
            whole < 0 -> "−${-whole}"
            else -> "0"
        }
    }
}
