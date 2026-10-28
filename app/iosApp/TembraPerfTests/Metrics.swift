import XCTest

/// The metrics every journey collects (contracts/metric-catalogue.md): wall clock, CPU, memory, the catalogue sections
/// the journey exercises as `XCTOSSignpostMetric(subsystem: "audio.tembra", category: "ui", name:)`, and the
/// animation signpost of the tempo change for the frame-health family. Section names are the signpost names of
/// `app/shared/engine/perf-catalogue.txt`. Every measurement runs `iterationCount` iterations.
enum Metrics {
    static let iterationCount = 20
    static let subsystem = "audio.tembra"

    static var options: XCTMeasureOptions {
        let options = XCTMeasureOptions()
        options.iterationCount = iterationCount
        options.invocationOptions = [.manuallyStart, .manuallyStop]
        return options
    }

    static let tunerSections = ["tuner.load", "tuner.modeSwitch", "tuner.frame"]
    static let metronomeSections = ["metronome.load", "metronome.tempoChange"]
    /// Feature 009 (contracts/perf-instrumentation.md): the timeline journey's sections; the engine's own are emitted
    /// from C++ under the "engine" category. The master pop-up's (design iteration D1) has no interval where the bar is
    /// wide enough for the inline slider.
    static let timelineSections = ["timeline.projectOpen", "timeline.waveform", "transport.play", "transport.seek",
                                   "timeline.zoom", "timeline.scroll", "timeline.clipSplit", "timeline.playheadFrame",
                                   "transport.masterPopup"]
    static let timelineEngineSections = ["engine.prime", "engine.loadClip"]
    static let stressTimelineSections = ["timeline.scroll", "timeline.playheadFrame", "transport.play"]

    /// Feature 010 (contracts/perf-instrumentation.md): the recording journey's sections; `engine.takeAlignment` is the C++ one.
    static let recordingSections = ["timeline.arm", "transport.recordStart", "transport.countIn", "timeline.takeFrame",
                                    "transport.recordStop", "timeline.monitorToggle", "timeline.waveform", "transport.play",
                                    "timeline.playheadFrame", "timeline.projectSave"]
    static let recordingEngineSections = ["engine.takeAlignment"]

    static func section(_ name: String) -> XCTOSSignpostMetric {
        section(name, category: "ui")
    }

    /// A section under another category: the engine's C++ sections are recorded under "engine".
    static func section(_ name: String, category: String) -> XCTOSSignpostMetric {
        XCTOSSignpostMetric(subsystem: subsystem, category: category, name: name)
    }

    static func journey(_ name: String, app: XCUIApplication) -> [XCTMetric] {
        var metrics: [XCTMetric] = [XCTClockMetric(), XCTCPUMetric(application: app), XCTMemoryMetric(application: app)]
        metrics += sections(of: name).map { section($0) }
        metrics += engineSections(of: name).map { section($0, category: "engine") }
        return metrics
    }

    static func sections(of journey: String) -> [String] {
        switch journey {
        case "tuner": return tunerSections
        case "metronome": return metronomeSections
        case "timeline": return timelineSections
        case "recording": return recordingSections
        case "stress": return tunerSections + metronomeSections + stressTimelineSections
        default: return []
        }
    }

    static func engineSections(of journey: String) -> [String] {
        switch journey {
        case "timeline": return timelineEngineSections
        case "recording": return recordingEngineSections
        default: return []
        }
    }
}
