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

    static func section(_ name: String) -> XCTOSSignpostMetric {
        XCTOSSignpostMetric(subsystem: subsystem, category: "ui", name: name)
    }

    static func journey(_ name: String, app: XCUIApplication) -> [XCTMetric] {
        var metrics: [XCTMetric] = [XCTClockMetric(), XCTCPUMetric(application: app), XCTMemoryMetric(application: app)]
        metrics += sections(of: name).map(section)
        return metrics
    }

    static func sections(of journey: String) -> [String] {
        switch journey {
        case "tuner": return tunerSections
        case "metronome": return metronomeSections
        case "stress": return tunerSections + metronomeSections
        default: return []
        }
    }
}
