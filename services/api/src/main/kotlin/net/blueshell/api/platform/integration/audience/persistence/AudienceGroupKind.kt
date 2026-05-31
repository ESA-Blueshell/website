package net.blueshell.api.platform.integration.audience.persistence

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Native-side shape of an audience on its external system.
 *
 * Brevo audiences are mailing lists; Discord audiences are server roles;
 * Google Workspace audiences are groups. The adapter for each
 * [net.blueshell.api.platform.integration.sync.port.TargetSystem] knows
 * which kinds it supports.
 */
@Schema(enumAsRef = true)
enum class AudienceGroupKind {
    LIST,
    ROLE,
    GROUP,
}
