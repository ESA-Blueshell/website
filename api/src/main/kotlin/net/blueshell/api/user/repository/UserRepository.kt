package net.blueshell.api.user.repository

import net.blueshell.api.user.model.User
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : BaseRepository<User, Long> {
    fun findByUsername(username: String): Optional<User>

    fun existsByUsername(username: String): Boolean

    fun existsByUsernameAndIdNot(username: String, id: Long): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByDiscord(discord: String): Boolean

    fun existsByDiscordAndIdNot(discord: String, id: Long): Boolean

    fun existsByEmailAndIdNot(email: String, id: Long): Boolean

    fun existsByPhoneNumber(phoneNumber: String): Boolean

    fun existsByPhoneNumberAndIdNot(phoneNumber: String, id: Long): Boolean
}
