import Network
import UIKit
import XCTest

/// The device checks the host cannot read (spec FR-004): thermal state (must be nominal) and battery level (at least
/// the minimum on devices; not checked on simulators) refuse the round with a failure message starting
/// `PERF-REFUSED:`; the network state is recorded, never refused. Everything lands in the `preflight.json`
/// attachment the collector exports.
enum Preflight {
    static let batteryMinimum: Float = 0.5

    struct Result {
        let thermalState: String
        let batteryLevel: Float
        let networkState: String
        let simulator: Bool
        var refusal: String?

        var json: String {
            let refusalField = refusal.map { "\"refusal\":\"\($0)\"" } ?? "\"refusal\":null"
            return "{\"schema\":1,\"thermal_state\":\"\(thermalState)\",\"battery_level\":\(batteryLevel),\"network_state\":\"\(networkState)\",\"simulator\":\(simulator),\(refusalField)}"
        }
    }

    static func run() -> Result {
        let thermal = thermalName(ProcessInfo.processInfo.thermalState)
        #if targetEnvironment(simulator)
        let simulator = true
        let battery: Float = 1
        #else
        let simulator = false
        UIDevice.current.isBatteryMonitoringEnabled = true
        let battery = UIDevice.current.batteryLevel
        #endif
        var result = Result(thermalState: thermal, batteryLevel: battery, networkState: currentNetworkState(), simulator: simulator, refusal: nil)
        if thermal != "nominal" {
            result.refusal = "thermal_state=\(thermal) limit=nominal"
        } else if !simulator && battery >= 0 && battery < batteryMinimum {
            result.refusal = "battery_level=\(battery) limit=\(batteryMinimum)"
        }
        return result
    }

    static func thermalName(_ state: ProcessInfo.ThermalState) -> String {
        switch state {
        case .nominal: return "nominal"
        case .fair: return "fair"
        case .serious: return "serious"
        case .critical: return "critical"
        @unknown default: return "unknown"
        }
    }

    /// `none`, `wifi`, `cellular` or `unknown` from one path-monitor update.
    static func currentNetworkState() -> String {
        let monitor = NWPathMonitor()
        let group = DispatchGroup()
        var state = "unknown"
        group.enter()
        var reported = false
        monitor.pathUpdateHandler = { path in
            guard !reported else { return }
            reported = true
            if path.status != .satisfied {
                state = "none"
            } else if path.usesInterfaceType(.wifi) || path.usesInterfaceType(.wiredEthernet) {
                state = "wifi"
            } else if path.usesInterfaceType(.cellular) {
                state = "cellular"
            } else {
                state = "unknown"
            }
            group.leave()
        }
        monitor.start(queue: DispatchQueue(label: "audio.tembra.perf.network"))
        _ = group.wait(timeout: .now() + 2)
        monitor.cancel()
        return state
    }
}
