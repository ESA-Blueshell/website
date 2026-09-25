package net.blueshell.api.auth.domain.twofactor

import net.blueshell.api.auth.domain.NothingToConfirm
import net.blueshell.api.auth.domain.SecurityEvents
import net.blueshell.api.auth.domain.TwoFactorOff
import net.blueshell.api.auth.domain.TwoFactorRequired
import net.blueshell.api.auth.domain.WrongCode
import net.blueshell.api.auth.persistence.BackupCode
import net.blueshell.api.auth.persistence.BackupCodeRepository
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.TwoFactorSecret
import net.blueshell.api.auth.persistence.TwoFactorSecretRepository
import net.blueshell.api.auth.persistence.TwoFactorSecretState
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Optional

class TwoFactorTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
    private val secrets = mock<TwoFactorSecretRepository>()
    private val backupCodes = mock<BackupCodeRepository>()
    private val cipher = SecretCipher("1", Base64.getEncoder().encodeToString(ByteArray(32) { 3 }), "")
    private val users = mock<UserService>()
    private val events = mock<SecurityEvents>()
    private val signIns = mock<SignIns>()
    private val trustedBrowsers = mock<TrustedBrowsers>()
    private val twoFactor =
        TwoFactor(secrets, backupCodes, cipher, users, events, signIns, trustedBrowsers, clock, "ESA Blueshell")

    private val user =
        User(
            username = "alice",
            email = "alice@example.com",
            password = "hash",
            initials = "A",
            firstName = "Alice",
            lastName = "Doe",
            roles = mutableSetOf(Role.MEMBER),
        ).also { it.id = 7 }

    private val raw = "12345678901234567890".toByteArray()

    private fun secret(state: TwoFactorSecretState, id: Long = 1) =
        cipher.seal(raw).let { TwoFactorSecret(user, it.ciphertext, it.keyId, state).also { s -> s.id = id } }

    private fun holding(state: TwoFactorSecretState, secret: TwoFactorSecret?) =
        whenever(secrets.findByUserAndState(7, state)).thenReturn(Optional.ofNullable(secret))

    private fun code() = Totp.code(raw, Totp.stepAt(clock.instant()))

    @BeforeEach
    fun setUp() {
        whenever(users.findById(7)).thenReturn(user)
        TwoFactorSecretState.entries.forEach { holding(it, null) }
    }

    @Test
    fun `standing says what is on, what is left and whether the offer or a set-up is due`() {
        assertThat(twoFactor.standing(7)).isEqualTo(TwoFactorStanding(on = false, backupCodesLeft = 0, required = false, offered = true))

        user.twoFactorSince = clock.instant()
        holding(TwoFactorSecretState.ACTIVE, secret(TwoFactorSecretState.ACTIVE))
        whenever(backupCodes.findUnused(1)).thenReturn(listOf(BackupCode(secret(TwoFactorSecretState.ACTIVE), "h")))
        assertThat(twoFactor.standing(7)).isEqualTo(
            TwoFactorStanding(
                on = true,
                backupCodesLeft = 1,
                required = false,
                offered = false,
                mayTurnOff = true,
                since = clock.instant(),
            ),
        )

        user.twoFactorSince = null
        user.roles = mutableSetOf(Role.BOARD)
        assertThat(twoFactor.standing(7).required).isTrue()
        assertThat(twoFactor.standing(7).offered).isFalse()
        user.twoFactorSince = clock.instant()
        assertThat(twoFactor.standing(7).mayTurnOff).isFalse()
    }

    @Test
    fun `setting up answers a sealed secret in both forms`() {
        val half = secret(TwoFactorSecretState.CONFIRMED, id = 9)
        whenever(secrets.findSettingUp(7)).thenReturn(listOf(half))

        val pending = twoFactor.setUp(7)

        verify(secrets).purgeBackupCodesOfSecret(9)
        verify(secrets).purgeSecret(9)
        val saved = argumentCaptor<TwoFactorSecret>()
        verify(secrets).save(saved.capture())
        assertThat(saved.firstValue.state).isEqualTo(TwoFactorSecretState.PENDING)
        assertThat(saved.firstValue.user).isSameAs(user)
        assertThat(cipher.open(SealedSecret(saved.firstValue.keyId, saved.firstValue.ciphertext))).isEqualTo(Base32.decode(pending.key))
        assertThat(pending.otpauthUri).startsWith("otpauth://totp/ESA%20Blueshell:alice?secret=${pending.key}")
    }

    @Test
    fun `the first right code proves the secret and hands out ten backup codes`() {
        assertThrows<NothingToConfirm> { twoFactor.confirm(7, code()) }
        val pending = secret(TwoFactorSecretState.PENDING)
        holding(TwoFactorSecretState.PENDING, pending)
        assertThrows<WrongCode> { twoFactor.confirm(7, "000000") }

        val codes = twoFactor.confirm(7, code())

        assertThat(codes).hasSize(10)
        assertThat(pending.state).isEqualTo(TwoFactorSecretState.CONFIRMED)
        assertThat(pending.confirmedAt).isEqualTo(clock.instant())
        assertThat(pending.lastUsedStep).isEqualTo(Totp.stepAt(clock.instant()))
        val saved = argumentCaptor<List<BackupCode>>()
        verify(backupCodes).saveAll(saved.capture())
        assertThat(saved.firstValue.map { it.secret }.distinct()).containsExactly(pending)
    }

    @Test
    fun `saving turns it on, ends the other sign-ins and tells the person`() {
        assertThrows<NothingToConfirm> { twoFactor.saved(7, "here") }
        val confirmed = secret(TwoFactorSecretState.CONFIRMED)
        holding(TwoFactorSecretState.CONFIRMED, confirmed)
        user.awaitingReenrolment = true

        twoFactor.saved(7, "here")

        assertThat(confirmed.state).isEqualTo(TwoFactorSecretState.ACTIVE)
        assertThat(user.twoFactorSince).isEqualTo(clock.instant())
        assertThat(user.awaitingReenrolment).isFalse()
        verify(trustedBrowsers).forgetAll(7)
        verify(signIns).endAll(7, "here")
        verify(signIns).recordStepUp("here", "otp")
        verify(events).record(eq(7L), eq(SecurityEventKind.TWO_FACTOR_ON), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `saving a new app over the active one replaces it`() {
        holding(TwoFactorSecretState.CONFIRMED, secret(TwoFactorSecretState.CONFIRMED, id = 2))
        holding(TwoFactorSecretState.ACTIVE, secret(TwoFactorSecretState.ACTIVE, id = 1))
        user.twoFactorSince = Instant.EPOCH

        twoFactor.saved(7, null)

        verify(secrets).purgeSecret(1)
        assertThat(user.twoFactorSince).isEqualTo(Instant.EPOCH)
        verify(signIns, never()).recordStepUp(any(), anyOrNull())
        verify(events).record(eq(7L), eq(SecurityEventKind.TWO_FACTOR_REPLACED), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `only somebody holding no granted role may turn it off`() {
        assertThrows<TwoFactorOff> { twoFactor.turnOff(7) }
        user.twoFactorSince = clock.instant()
        user.roles = mutableSetOf(Role.ADMIN)
        assertThrows<TwoFactorRequired> { twoFactor.turnOff(7) }

        user.roles = mutableSetOf(Role.MEMBER)
        twoFactor.turnOff(7)

        assertThat(user.twoFactorSince).isNull()
        verify(secrets).purgeSecretsOf(7)
        verify(events).record(eq(7L), eq(SecurityEventKind.TWO_FACTOR_OFF), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `regenerating backup codes replaces them all`() {
        assertThrows<TwoFactorOff> { twoFactor.regenerateBackupCodes(7) }
        holding(TwoFactorSecretState.ACTIVE, secret(TwoFactorSecretState.ACTIVE))

        assertThat(twoFactor.regenerateBackupCodes(7)).hasSize(10)

        verify(secrets).purgeBackupCodesOfSecret(1)
        verify(events).record(eq(7L), eq(SecurityEventKind.BACKUP_CODES_REGENERATED), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `a proof is an authenticator code once per step, or a backup code once`() {
        assertThat(twoFactor.prove(7, code())).isNull()
        val active = secret(TwoFactorSecretState.ACTIVE)
        holding(TwoFactorSecretState.ACTIVE, active)

        assertThat(twoFactor.prove(7, code())).isEqualTo(Proof.AUTHENTICATOR_CODE)
        assertThat(twoFactor.prove(7, code())).isNull()

        val backup = BackupCode(active, BackupCodes.hash("abcde-fghjk"))
        whenever(backupCodes.findUnused(1)).thenReturn(listOf(backup))
        assertThat(twoFactor.prove(7, "zzzzz-zzzzz")).isNull()
        assertThat(twoFactor.prove(7, "ABCDE FGHJK")).isEqualTo(Proof.BACKUP_CODE)
        assertThat(backup.usedAt).isEqualTo(clock.instant())
        verify(events).record(eq(7L), eq(SecurityEventKind.BACKUP_CODE_USED), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `the offer is answered once`() {
        twoFactor.answerOffer(7)
        clock.advance(Duration.ofDays(1))
        twoFactor.answerOffer(7)

        assertThat(user.twoFactorOfferAnsweredAt).isEqualTo(Instant.parse("2026-09-24T12:00:00Z"))
        verify(users).update(user)
    }

    @Test
    fun `erasing leaves nothing behind, and writes the person only when there was something on`() {
        twoFactor.erase(7)
        verify(users, never()).update(any())

        user.twoFactorSince = clock.instant()
        twoFactor.erase(7)

        assertThat(user.twoFactorSince).isNull()
        verify(secrets, times(2)).purgeBackupCodesOf(7)
        verify(trustedBrowsers, times(2)).forgetAll(7)
        verify(users).update(user)
    }
}
