package net.blueshell.api.platform.integration.cohort.persistence

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Native-side shape of a cohort on its external system.
 *
 * Brevo cohorts are mailing lists; Discord cohorts are server roles;
 * Google Workspace cohorts are groups. The adapter for each
 * [net.blueshell.api.platform.integration.sync.port.TargetSystem] knows
 * which kinds it supports.
 */
@Schema(enumAsRef = true)
enum class CohortKind {
    LIST,
    ROLE,
    GROUP,
}
