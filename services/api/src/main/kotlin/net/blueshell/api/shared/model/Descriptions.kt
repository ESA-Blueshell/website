package net.blueshell.api.shared.model

/**
 * The most a description holds (architecture ADR-010). An event's Discord post shows it whole but
 * for the last few hundred characters, which share the post with its details. The frontend's
 * DESCRIPTION_CAP is the same number; change one, change the other.
 */
const val DESCRIPTION_MAX = 4096
