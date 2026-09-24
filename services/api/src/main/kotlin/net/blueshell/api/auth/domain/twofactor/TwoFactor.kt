package net.blueshell.api.auth.domain.twofactor

import net.blueshell.api.auth.domain.NothingToConfirm
import net.blueshell.api.auth.domain.SecurityEvents
import net.blueshell.api.auth.domain.TwoFactorOff
import net.blueshell.api.auth.domain.TwoFactorRequired
import net.blueshell.api.auth.domain.WrongCode
import net.blueshell.api.auth.domain.WrongPassword
import net.blueshell.api.auth.persistence.BackupCode
import net.blueshell.api.auth.persistence.BackupCodeRepository
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.TwoFactorSecret
import net.blueshell.api.auth.persistence.TwoFactorSecretRepository
import net.blueshell.api.auth.persistence.TwoFactorSecretState
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignIns
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import kotlin.jvm.optionals.getOrNull

/** Where somebody stands with two-factor, as the security page and the sign-in answer read it. */
data class TwoFactorStanding(
    val on: Boolean,
    val backupCodesLeft: Int,
    val required: Boolean,
    val offered: Boolean,
)

/** A secret being set up, in the two forms an authenticator app takes it. */
data class PendingSecret(
    val otpauthUri: String,
    val key: String,
)

/** How a code given at a challenge or a step-up was taken. */
enum class Proof { AUTHENTICATOR_CODE, BACKUP_CODE }

/**
 * Setting up, keeping and taking off an authenticator app and its backup codes (api ADR-031).
 *
 * Set-up is three steps: the password, which shows a secret; a first right code, which proves it
 * and shows the backup codes; and the person saying they saved those, which turns it on. Replacing
 * an app runs the same steps beside the active secret, which keeps working until the new one is on.
 */
@Service
// One surface for the person's own two-factor, as the endpoints are.
@Suppress("TooManyFunctions")
class TwoFactor(
    private val secrets: TwoFactorSecretRepository,
    private val backupCodes: BackupCodeRepository,
    private val cipher: SecretCipher,
    private val users: UserService,
    private val passwords: PasswordEncoder,
    private val events: SecurityEvents,
    private val signIns: SignIns,
    private val trustedBrowsers: TrustedBrowsers,
    private val clock: Clock,
    @param:Value($$"${app.two-factor.issuer:ESA Blueshell}") private val issuer: String,
) {
    @Transactional(readOnly = true)
    fun standing(userId: Long): TwoFactorStanding {
        val user = users.findById(userId)
        val active = active(userId)
        return TwoFactorStanding(
            on = user.hasTwoFactor,
            backupCodesLeft = active?.let { backupCodes.findUnused(requireNotNull(it.id)).size } ?: 0,
            required = user.dormantRoles.isNotEmpty(),
            offered = !user.hasTwoFactor && user.twoFactorOfferAnsweredAt == null && !user.holdsGrantedRole,
        )
    }

    /** Starts setting up, or replacing, an authenticator app. Any secret half set up is dropped. */
    @Transactional
    fun setUp(
        userId: Long,
        password: String,
    ): PendingSecret {
        val user = users.findById(userId)
        if (!passwords.matches(password, user.password)) throw WrongPassword()
        secrets.findSettingUp(userId).forEach { purge(requireNotNull(it.id)) }
        val raw = Totp.newSecret()
        val sealed = cipher.seal(raw)
        secrets.save(TwoFactorSecret(user = user, ciphertext = sealed.ciphertext, keyId = sealed.keyId))
        val key = Base32.encode(raw)
        return PendingSecret(Totp.otpauthUri(issuer, user.username, key), key)
    }

    /** The first right code proves the secret; the answer is its ten backup codes, shown this once. */
    @Transactional
    fun confirm(
        userId: Long,
        code: String,
    ): List<String> {
        val pending = secrets.findByUserAndState(userId, TwoFactorSecretState.PENDING).getOrNull() ?: throw NothingToConfirm()
        val step = Totp.acceptedStep(open(pending), code, clock.instant(), pending.lastUsedStep) ?: throw WrongCode(null)
        pending.lastUsedStep = step
        pending.state = TwoFactorSecretState.CONFIRMED
        pending.confirmedAt = clock.instant()
        secrets.save(pending)
        return issueBackupCodes(pending)
    }

    /**
     * The person saved their backup codes: two-factor is on from here, or the new app replaces the
     * old one. Every other sign-in ends; this one carries on, proved by the code just given.
     */
    @Transactional
    fun saved(
        userId: Long,
        signInId: String?,
    ) {
        val confirmed = secrets.findByUserAndState(userId, TwoFactorSecretState.CONFIRMED).getOrNull() ?: throw NothingToConfirm()
        val replaced = active(userId)
        replaced?.let { purge(requireNotNull(it.id)) }
        confirmed.state = TwoFactorSecretState.ACTIVE
        confirmed.activatedAt = clock.instant()
        secrets.save(confirmed)

        val user = users.findById(userId)
        user.twoFactorSince = user.twoFactorSince ?: clock.instant()
        user.awaitingReenrolment = false
        users.update(user)
        trustedBrowsers.forgetAll(userId)
        signIns.endAll(userId, keep = signInId)
        signInId?.let { signIns.recordStepUp(it, SignIn.METHOD_OTP) }
        events.record(userId, if (replaced == null) SecurityEventKind.TWO_FACTOR_ON else SecurityEventKind.TWO_FACTOR_REPLACED)
    }

    /** Only for somebody holding no granted role: for anybody else two-factor is replaced, never removed. */
    @Transactional
    fun turnOff(userId: Long) {
        val user = users.findById(userId)
        if (!user.hasTwoFactor) throw TwoFactorOff()
        if (user.holdsGrantedRole) throw TwoFactorRequired()
        erase(userId)
        events.record(userId, SecurityEventKind.TWO_FACTOR_OFF)
    }

    @Transactional
    fun regenerateBackupCodes(userId: Long): List<String> {
        val secret = active(userId) ?: throw TwoFactorOff()
        secrets.purgeBackupCodesOfSecret(requireNotNull(secret.id))
        val codes = issueBackupCodes(secret)
        events.record(userId, SecurityEventKind.BACKUP_CODES_REGENERATED)
        return codes
    }

    /**
     * Takes an authenticator code or a backup code for [userId], or refuses it. A code is spent
     * the moment it is taken: an authenticator code's time step cannot come round again, and a
     * backup code is marked used.
     */
    @Transactional
    fun prove(
        userId: Long,
        typed: String,
    ): Proof? {
        val secret = active(userId) ?: return null
        if (BackupCodes.looksLikeOne(typed)) {
            val hash = BackupCodes.hash(typed)
            val code = backupCodes.findUnused(requireNotNull(secret.id)).firstOrNull { it.codeHash == hash } ?: return null
            code.usedAt = clock.instant()
            backupCodes.save(code)
            events.record(userId, SecurityEventKind.BACKUP_CODE_USED)
            return Proof.BACKUP_CODE
        }
        val step = Totp.acceptedStep(open(secret), typed, clock.instant(), secret.lastUsedStep) ?: return null
        secret.lastUsedStep = step
        secrets.save(secret)
        return Proof.AUTHENTICATOR_CODE
    }

    @Transactional
    fun answerOffer(userId: Long) {
        val user = users.findById(userId)
        if (user.twoFactorOfferAnsweredAt != null) return
        user.twoFactorOfferAnsweredAt = clock.instant()
        users.update(user)
    }

    /** Removes the person's second factor entirely: secrets, codes and trusted browsers, for good. */
    @Transactional
    fun erase(userId: Long) {
        secrets.purgeBackupCodesOf(userId)
        secrets.purgeSecretsOf(userId)
        trustedBrowsers.forgetAll(userId)
        val user = users.findById(userId)
        if (user.twoFactorSince != null) {
            user.twoFactorSince = null
            users.update(user)
        }
    }

    private fun active(userId: Long): TwoFactorSecret? =
        secrets.findByUserAndState(userId, TwoFactorSecretState.ACTIVE).getOrNull()

    private fun open(secret: TwoFactorSecret): ByteArray = cipher.open(SealedSecret(secret.keyId, secret.ciphertext))

    private fun issueBackupCodes(secret: TwoFactorSecret): List<String> {
        val codes = BackupCodes.generate()
        backupCodes.saveAll(codes.map { BackupCode(secret = secret, codeHash = BackupCodes.hash(it)) })
        return codes
    }

    private fun purge(secretId: Long) {
        secrets.purgeBackupCodesOfSecret(secretId)
        secrets.purgeSecret(secretId)
    }
}
