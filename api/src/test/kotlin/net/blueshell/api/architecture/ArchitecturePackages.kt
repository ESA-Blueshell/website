package net.blueshell.api.architecture

/**
 * Central place for package definitions used by ArchUnit rules.
 * Aligned with ADR-001 (Multi-Layered DDD Architecture) and ADR-016 (Layer Dependency Rules).
 */
object ArchitecturePackages {
    const val ROOT = "net.blueshell.api"

    // ===== Layer Packages (ADR-001) =====

    /** Web Layer - Controllers, DTOs, Web Validators */
    const val WEB = "$ROOT..web.."
    const val CONTROLLER = "$ROOT..web..*Controller"
    const val DTO = "$ROOT..web.dto.."
    const val WEB_VALIDATION = "$ROOT..web.validation.."
    const val WEB_MAPPING = "$ROOT..web.mapping.."

    /** Command Layer - Command objects (ADR-002) */
    const val COMMAND = "$ROOT..command.."

    /** Application Layer - Services, Handlers, Business Validators, Listeners, Factories */
    const val APPLICATION = "$ROOT..application.."
    const val SERVICE = "$ROOT..application..*Service"
    const val COMMAND_HANDLER = "$ROOT..application.command.."
    const val APPLICATION_VALIDATION = "$ROOT..application.validation.."
    const val LISTENER = "$ROOT..application.listener.."
    const val EVENT = "$ROOT..application.event.."
    const val FACTORY = "$ROOT..application.factory.."
    const val QUERY = "$ROOT..application.query.."

    /** Domain Layer - Optional rich domain models and domain services */
    const val DOMAIN = "$ROOT..domain.."
    const val DOMAIN_MODEL = "$ROOT..domain.model.."
    const val DOMAIN_SERVICE = "$ROOT..domain.service.."

    /** Persistence Layer - Entities, Repositories, Specifications */
    const val PERSISTENCE = "$ROOT..persistence.."
    const val ENTITY = "$ROOT..persistence..*"
    const val REPOSITORY = "$ROOT..persistence.repository.."
    const val SPECIFICATION = "$ROOT..persistence.spec.."

    // ===== Infrastructure Layer (ADR-014, ADR-019) =====

    /** Infrastructure - Cross-cutting concerns */
    const val INFRASTRUCTURE = "$ROOT.infrastructure.."
    const val SECURITY = "$ROOT.infrastructure.security.."
    const val PERMISSION = "$ROOT.infrastructure.security.permission.."

    /** Platform - Integration with external systems */
    const val PLATFORM = "$ROOT.platform.."
    const val PLATFORM_CONFIG = "$ROOT.platform.config.."
    const val PLATFORM_INTEGRATION = "$ROOT.platform.integration.."
    const val JOB = "$ROOT.platform.integration..job.."
    const val ACL_ADAPTER = "$ROOT.platform.integration..*Adapter"

    // ===== Shared Kernel (ADR-020) =====

    /** Shared - Common utilities, enums, base classes */
    const val SHARED = "$ROOT.shared.."
    const val SHARED_COMMAND = "$ROOT.shared.command.."
    const val SHARED_VALIDATION = "$ROOT.shared.validation.."
    const val SHARED_MODEL = "$ROOT.shared.model.."
    const val SHARED_ENUM = "$ROOT.shared.enums.."

    // ===== Domain-Specific Packages =====

    /** Domain Boundaries (ADR-017, ADR-018) */
    const val DOMAIN_AUTH = "$ROOT.domain.auth.."
    const val DOMAIN_USER = "$ROOT.domain.user.."
    const val DOMAIN_COMMITTEE = "$ROOT.domain.committee.."
    const val DOMAIN_EVENT = "$ROOT.domain.event.."
    const val DOMAIN_SURVEY = "$ROOT.domain.survey.."
    const val DOMAIN_MEMBERSHIP = "$ROOT.domain.membership.."
    const val DOMAIN_CONTRIBUTION = "$ROOT.domain.contribution.."
    const val DOMAIN_SPONSOR = "$ROOT.domain.sponsor.."
    const val DOMAIN_BOARD = "$ROOT.domain.board.."
    const val DOMAIN_FILE = "$ROOT.domain.file.."
}
