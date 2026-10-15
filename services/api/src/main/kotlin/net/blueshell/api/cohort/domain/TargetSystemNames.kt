package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.enums.TargetSystem

/** How a system is named as the first step of a target's path, and in the order systems are listed. */
internal val TargetSystem.shownName: String
    get() =
        when (this) {
            TargetSystem.BREVO -> "Brevo"
            TargetSystem.GOOGLE_CALENDAR -> "Google Calendar"
            TargetSystem.DISCORD -> "Discord"
        }
