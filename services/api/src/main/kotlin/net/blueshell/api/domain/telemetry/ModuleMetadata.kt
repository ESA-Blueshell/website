package net.blueshell.api.domain.telemetry

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Outbound link tracking: one `Telemetry` row per platform-and-URL pair the board publishes, and
 * one `Redirect` row for every click that passes through it.
 *
 * Reads are public because the redirect endpoint is the link itself; creating a tracked link is
 * a permissioned write.
 */
@PackageInfo
@ApplicationModule(
    id = "telemetry",
    allowedDependencies = [
        // Open kernel: TelemetryPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
    ],
)
class ModuleMetadata
