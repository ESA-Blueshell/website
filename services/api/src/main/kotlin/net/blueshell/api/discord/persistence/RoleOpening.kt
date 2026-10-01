package net.blueshell.api.discord.persistence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable

/** How far a role gets into a channel or category it opens: reading and writing, reading only, or joining and speaking. */
@Schema(name = "RoleAccess", enumAsRef = true)
enum class RoleAccess {
    WRITE,
    READ,
    SPEAK,
}

@Embeddable
data class RoleOpeningKey(
    @Column(name = "role_id", nullable = false, length = 32)
    val roleId: String = "",
    @Column(name = "channel_id", nullable = false, length = 32)
    val channelId: String = "",
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * A channel or category a role opens, at the access the board set on the site. Kept so the role's
 * page can say where Discord differs; the site writes Discord only when the board changes it.
 */
@Entity
@Table(name = "role_opening")
class RoleOpening(
    @EmbeddedId
    val key: RoleOpeningKey,
    @Enumerated(EnumType.STRING)
    @Column(name = "access", nullable = false, length = 16)
    var access: RoleAccess,
) : Identifiable<RoleOpeningKey> {
    override val id: RoleOpeningKey get() = key
}

interface RoleOpeningRepository : JpaRepository<RoleOpening, RoleOpeningKey> {
    fun findAllByKeyRoleId(roleId: String): List<RoleOpening>
}
