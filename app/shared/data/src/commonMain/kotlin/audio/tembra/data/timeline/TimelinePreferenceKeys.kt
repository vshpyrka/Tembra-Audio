package audio.tembra.data.timeline

/** The string keys of the timeline's remembered choices, inside the app's one preferences file. */
object TimelinePreferenceKeys {
    /** Whether the overview strip shows (spec FR-015): a boolean, absent means shown. */
    const val OVERVIEW_STRIP = "timeline.overviewStrip"

    /** Whether the armed track's input is monitored (feature 010, contracts/take-storage.md): a boolean, absent means off. */
    const val MONITOR_INPUT = "timeline.monitorInput"

    /**
     * The D1 comparison's zoom-control choice ("buttons" or "overviewStrip"), retired once the owner kept the strip
     * (T144): nothing reads it, and the first write of [OVERVIEW_STRIP] removes it from devices that still carry it.
     */
    const val RETIRED_ZOOM_CONTROL = "timeline.zoomControl"
}
