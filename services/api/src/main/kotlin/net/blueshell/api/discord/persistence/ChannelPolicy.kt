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

/** How far somebody gets into a channel: not at all, reading it, or writing in it too. */
@Schema(name = "ChannelAccess", enumAsRef = true)
enum class ChannelAccess {
    HIDDEN,
    READ,
    WRITE,
}

/**
 * The access a games or esports channel was set to on the site, for everybody and for members. Kept
 * so the site can say where Discord differs; it never writes it back on its own.
 */
@Entity
@Table(name = "channel_policy")
class ChannelPolicy(
    @Id
    @Column(name = "channel_id", nullable = false, length = 32)
    val channelId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "everyone", nullable = false, length = 16)
    var everyone: ChannelAccess,
    @Enumerated(EnumType.STRING)
    @Column(name = "members", nullable = false, length = 16)
    var members: ChannelAccess,
) : Identifiable<String> {
    override val id: String get() = channelId
}

interface ChannelPolicyRepository : JpaRepository<ChannelPolicy, String>
