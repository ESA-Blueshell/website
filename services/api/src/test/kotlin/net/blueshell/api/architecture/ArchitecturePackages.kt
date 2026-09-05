package net.blueshell.api.architecture

/**
 * Central place for package definitions used by ArchUnit rules.
 *
 * Every constant here names a package that holds at least one class today, and
 * [PackageConstantsArchitectureTest] fails if one stops doing so. A glob that matches nothing is
 * silently satisfiable, so a stale constant turns its rules green rather than red.
 *
 * The shape is architecture ADR-003's: modules are flat under the base package and each holds
 * `api`, `domain`, `persistence` and `web` directly.
 */
object ArchitecturePackages {
    const val ROOT = "net.blueshell.api"

    /** Controllers, input types, responses and their mappers, in any module. */
    const val WEB = "$ROOT..web.."

    /**
     * A module's four folders. Written with a single `*` segment so they match a module's own
     * folder and not a same-named package nested deeper.
     */
    const val MODULE_DOMAIN = "$ROOT.*.domain.."
    const val MODULE_API = "$ROOT.*.api.."
    const val MODULE_WEB = "$ROOT.*.web.."
    const val MODULE_PERSISTENCE = "$ROOT.*.persistence.."

    /** Where a module's services live: the published ones in `api`, the rest in `domain`. */
    val SERVICE_LAYER = arrayOf(MODULE_API, MODULE_DOMAIN)

    /** Entities, repositories and specifications, in any module. */
    const val PERSISTENCE = "$ROOT..persistence.."

    /**
     * Cross-cutting security. Architecture ADR-003 makes this a top-level module of its own;
     * the `infrastructure` package these used to name was removed by the flattening.
     */
    const val SECURITY = "$ROOT.security.."

    /** ADR-007: only the base and composite evaluator stay here, never a `*Permission`. */
    const val PERMISSION = "$ROOT.security.permission.."

    /** Platform - global wiring and the profile-scoped doubles, not a module. */
    const val PLATFORM = "$ROOT.platform.."
    const val PLATFORM_CONFIG = "$ROOT.platform.config.."
    const val PLATFORM_INTEGRATION = "$ROOT.platform.integration.."

    /** Mock/test adapter implementations */
    const val PLATFORM_MOCK = "$ROOT.platform.integration.mock.."

    /**
     * Where a capability module's job handlers sit. The flattening left them in the module's own
     * `domain` or `api` folder rather than in a `job` sub-package of its own.
     */
    val JOB_HOMES = arrayOf(MODULE_DOMAIN, MODULE_API)

    /** Shared - Common utilities, enums, base classes */
    const val SHARED = "$ROOT.shared.."
    const val SHARED_MODEL = "$ROOT.shared.model.."
    const val SHARED_ENUM = "$ROOT.shared.enums.."
    const val SHARED_SECURITY = "$ROOT.shared.security.."
}
