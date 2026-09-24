package net.blueshell.api.auth.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface TwoFactorSecretRepository : BaseRepository<TwoFactorSecret, Long> {
    @Query("SELECT s FROM TwoFactorSecret s WHERE s.user.id = :userId AND s.state = :state")
    fun findByUserAndState(
        @Param("userId") userId: Long,
        @Param("state") state: TwoFactorSecretState,
    ): Optional<TwoFactorSecret>

    @Query("SELECT s FROM TwoFactorSecret s WHERE s.user.id = :userId AND s.state <> net.blueshell.api.auth.persistence.TwoFactorSecretState.ACTIVE")
    fun findSettingUp(
        @Param("userId") userId: Long,
    ): List<TwoFactorSecret>

    /** Gone for good, codes first: an erased or reset factor leaves no ciphertext behind. */
    @Modifying
    @Query(
        value = "DELETE FROM backup_codes WHERE secret_id IN (SELECT id FROM two_factor_secrets WHERE user_id = :userId)",
        nativeQuery = true,
    )
    fun purgeBackupCodesOf(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query(value = "DELETE FROM two_factor_secrets WHERE user_id = :userId", nativeQuery = true)
    fun purgeSecretsOf(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query(value = "DELETE FROM backup_codes WHERE secret_id = :secretId", nativeQuery = true)
    fun purgeBackupCodesOfSecret(
        @Param("secretId") secretId: Long,
    ): Int

    @Modifying
    @Query(value = "DELETE FROM two_factor_secrets WHERE id = :secretId", nativeQuery = true)
    fun purgeSecret(
        @Param("secretId") secretId: Long,
    ): Int
}

interface BackupCodeRepository : BaseRepository<BackupCode, Long> {
    @Query("SELECT c FROM BackupCode c WHERE c.secret.id = :secretId AND c.usedAt IS NULL")
    fun findUnused(
        @Param("secretId") secretId: Long,
    ): List<BackupCode>
}

interface TrustedBrowserRepository : BaseRepository<TrustedBrowser, Long> {
    fun findBySelector(selector: String): Optional<TrustedBrowser>

    @Query("SELECT b FROM TrustedBrowser b WHERE b.user.id = :userId ORDER BY b.trustedAt DESC")
    fun findOf(
        @Param("userId") userId: Long,
    ): List<TrustedBrowser>

    @Modifying
    @Query(value = "DELETE FROM trusted_browsers WHERE user_id = :userId", nativeQuery = true)
    fun purgeOf(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query(value = "DELETE FROM trusted_browsers WHERE id = :id", nativeQuery = true)
    fun purge(
        @Param("id") id: Long,
    ): Int
}
