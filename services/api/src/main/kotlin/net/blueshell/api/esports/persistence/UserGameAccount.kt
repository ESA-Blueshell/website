package net.blueshell.api.esports.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

/**
 * What one member is called in one game.
 *
 * Held once per member per game rather than copied onto every roster they appear on, so a
 * member who changes their handle changes it everywhere, across every season at once.
 */
@Entity
@Table(
    name = "user_game_account",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_game_account", columnNames = ["user_id", "game", "deleted_at"]),
    ],
    indexes = [
        Index(name = "idx_user_game_account_game", columnList = "game, deleted_at"),
        Index(name = "idx_user_game_account_del", columnList = "deleted_at"),
    ],
)
@SQLDelete(
    sql = "UPDATE user_game_account SET deleted_at = NOW(6), version = version + 1 WHERE id = ? AND version = ?",
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class UserGameAccount(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "game", nullable = false, length = 32)
    var game: String,

    @Column(name = "handle", nullable = false, length = 128)
    var handle: String,
) : AuditedAutoIdEntity()
