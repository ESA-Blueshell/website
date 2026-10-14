package net.blueshell.api.testsupport

import net.blueshell.api.discord.domain.DiscordSettings
import net.blueshell.api.discord.persistence.DiscordSettingRepository
import org.mockito.Mockito

/** Discord settings the board never set, so each reads as the configuration given here. */
fun configuredDiscordSettings(
    claimRoles: String = "",
    starboard: String = "starboard",
): DiscordSettings {
    // An unstubbed mock answers every lookup with an empty Optional: no setting was ever written.
    val unset = Mockito.mock(DiscordSettingRepository::class.java)
    return DiscordSettings(unset, "events-info", "events-calendar", starboard, claimRoles)
}
