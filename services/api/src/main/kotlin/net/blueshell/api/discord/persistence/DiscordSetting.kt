package net.blueshell.api.discord.persistence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable
import org.springframework.data.jpa.repository.JpaRepository

/** A Discord setting the board sets on the site. */
@Schema(name = "DiscordSettingName", enumAsRef = true)
enum class DiscordSettingName {
    /** The channel approved events are announced in, by name. */
    INFO_CHANNEL,

    /** The channel an event is posted in on its day, by name. */
    CALENDAR_CHANNEL,

    /** The channel the starboard bot reposts starred messages to, by name. */
    STARBOARD_CHANNEL,

    /** The roles the separate role-claim bot hands out, which the site leaves alone, comma apart. */
    CLAIM_ROLES,
}

/** One setting's value, as the board set it. A setting with no row is the application's configuration. */
@Entity
@Table(name = "discord_setting")
class DiscordSetting(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 32)
    val name: DiscordSettingName,
    @Column(name = "value", nullable = false, length = 1024)
    var value: String,
) : Identifiable<DiscordSettingName> {
    override val id: DiscordSettingName get() = name
}

interface DiscordSettingRepository : JpaRepository<DiscordSetting, DiscordSettingName>
