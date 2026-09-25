package net.blueshell.api.auth.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

/** A single-use code standing in for the authenticator app, stored as its SHA-256. */
@Entity
@Table(name = "backup_codes", indexes = [Index(name = "idx_backup_codes_secret_id", columnList = "secret_id")])
@SQLDelete(sql = "UPDATE backup_codes SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class BackupCode(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "secret_id", nullable = false)
    var secret: TwoFactorSecret,
    @Column(name = "code_hash", nullable = false, length = 64)
    var codeHash: String,
    @Column(name = "used_at")
    var usedAt: Instant? = null,
) : AuditedAutoIdEntity()
