package net.blueshell.api.discord.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.discord.persistence.DiscordSetting
import net.blueshell.api.discord.persistence.DiscordSettingName
import net.blueshell.api.discord.persistence.DiscordSettingRepository
import net.blueshell.api.sync.api.DiscordPostChannels
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Where the bots post and which roles the role-claim bot hands out, as the Discord settings page shows them. */
@Schema(name = "DiscordBotSettings")
data class DiscordBotSettings(
    @param:Schema(description = "The channel approved events are announced in, by name")
    val infoChannel: String,
    @param:Schema(description = "The channel an event is posted in on its day, by name")
    val calendarChannel: String,
    @param:Schema(description = "The channel the starboard bot reposts starred messages to, by name")
    val starboardChannel: String,
    @param:Schema(description = "The roles the role-claim bot hands out, which the site leaves alone")
    val claimRoleIds: List<String>,
)

/**
 * The Discord settings the board sets on the site. A setting the board never set is the
 * application's configuration, so a fresh server keeps working with the channel names it ships with.
 */
@Service
class DiscordSettings(
    private val settings: DiscordSettingRepository,
    @Value($$"${discord.posts.info-channel:events-info}") private val infoDefault: String,
    @Value($$"${discord.posts.calendar-channel:events-calendar}") private val calendarDefault: String,
    @Value($$"${discord.starboard.channel:starboard}") private val starboardDefault: String,
    @Value($$"${discord.claim-roles:}") private val claimRolesDefault: String,
) : DiscordPostChannels {
    override fun info(): String = valueOf(DiscordSettingName.INFO_CHANNEL, infoDefault)

    override fun calendar(): String = valueOf(DiscordSettingName.CALENDAR_CHANNEL, calendarDefault)

    fun starboard(): String = valueOf(DiscordSettingName.STARBOARD_CHANNEL, starboardDefault)

    fun claimRoleIds(): Set<String> = idsOf(valueOf(DiscordSettingName.CLAIM_ROLES, claimRolesDefault)).toSet()

    @Transactional(readOnly = true)
    fun read(): DiscordBotSettings = DiscordBotSettings(info(), calendar(), starboard(), claimRoleIds().sorted())

    @Transactional
    fun write(next: DiscordBotSettings): DiscordBotSettings {
        keep(DiscordSettingName.INFO_CHANNEL, next.infoChannel.trim().removePrefix("#"))
        keep(DiscordSettingName.CALENDAR_CHANNEL, next.calendarChannel.trim().removePrefix("#"))
        keep(DiscordSettingName.STARBOARD_CHANNEL, next.starboardChannel.trim().removePrefix("#"))
        keep(DiscordSettingName.CLAIM_ROLES, idsOf(next.claimRoleIds.joinToString(",")).distinct().joinToString(","))
        return read()
    }

    // A channel left blank goes back to the configuration rather than to nowhere.
    private fun keep(
        name: DiscordSettingName,
        value: String,
    ) {
        if (value.isEmpty() && name != DiscordSettingName.CLAIM_ROLES) return settings.deleteById(name)
        settings.save(settings.findById(name).orElse(null)?.also { it.value = value } ?: DiscordSetting(name, value))
    }

    private fun valueOf(
        name: DiscordSettingName,
        fallback: String,
    ): String = settings.findById(name).map { it.value }.orElse(fallback)

    private fun idsOf(value: String): List<String> = value.split(',').map(String::trim).filter(String::isNotEmpty)
}
