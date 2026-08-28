package net.blueshell.api.platform.integration.job.web.port

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * Not a surface. A module's `web` package is controllers, request and response types and their
 * mappers, and architecture ADR-003 records the reaches into it as "a defect rather than a
 * surface: they are inverted or copied, not published". They are named here only so the
 * whitelist that carries them says debt rather than `api`, and so a reach that is not already
 * pinned in `CrossModuleWebAccessArchitectureTest` fails verification.
 *
 * TODO: delete this declaration once the pinned reaches are gone.
 */
@PackageInfo
@NamedInterface("legacy-web")
class PackageMetadata
