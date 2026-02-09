package net.blueshell.api.architecture

/**
 * Central place for package definitions used by ArchUnit rules.
 */
object ArchitecturePackages {
    const val ROOT = "net.blueshell.api"

    const val DTO = "$ROOT..dto.."
    const val MODEL_BASE = "$ROOT..model.."
    const val MODEL = "$ROOT..model.."
    const val MODEL_CONVERTER = "$ROOT..model.converter.."

    const val CONTROLLER = "$ROOT..web.."
    const val MAPPER = "$ROOT..mapper.."
    const val VALIDATION = "$ROOT..validation.."

    const val SERVICE = "$ROOT..service.."
    const val REPOSITORY = "$ROOT..repository.."

    const val LISTENER = "$ROOT..listener.."
    const val LISTENER_JPA = "$ROOT.feature..listener.."
    const val JOB = "$ROOT..job.."

    const val CONFIG = "$ROOT.platform.config.."
    const val SECURITY = "$ROOT..security.."

    const val COMMON = "$ROOT.shared.."
}
