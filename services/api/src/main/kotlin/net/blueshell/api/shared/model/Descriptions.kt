package net.blueshell.api.shared.model

/**
 * The most a description holds: Discord's limit for an embed's description, so an event posted to
 * Discord shows whole (architecture ADR-010). The frontend's DESCRIPTION_CAP is the same number;
 * change one, change the other.
 */
const val DESCRIPTION_MAX = 4096
