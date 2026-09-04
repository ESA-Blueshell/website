package net.blueshell.api.cohort.persistence

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Native-side shape of a cohort on its external system. The adapter for each
 * [net.blueshell.api.shared.enums.TargetSystem] knows which kinds it supports.
 */
@Schema(enumAsRef = true)
enum class CohortKind {
    /** A Brevo mailing list. */
    LIST,

    /** A Discord server role. */
    ROLE,

    /** A Google Workspace group. */
    GROUP,
}
