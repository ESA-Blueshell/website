package net.blueshell.api.user.persistence

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.sql.Date
import java.time.LocalDate

class UserModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields and roles`() {
            val user = userFactory.createBasic()
            user.setUsername(unique("user"))
            user.setPassword("secret")
            user.firstName = "Ada"
            user.lastName = "Lovelace"
            user.prefix = "Dr."
            user.initials = "A.L."
            user.phoneNumber = "+31-20-000-0000"
            user.studentNumber = "S12345"
            user.dateOfBirth = Date.valueOf(LocalDate.of(1990, 1, 1))
            user.discord = "ada#1234"
            user.steamid = "12345678901234567"
            user.newsletter = true
            user.enabled = true
            user.consentPrivacy = true
            user.consentGdpr = true
            user.gender = "Other"
            user.photoConsent = true
            user.nationality = "Dutch"
            user.roles = mutableSetOf(Role.GUEST, Role.MEMBER)
            user.ehbo = true
            user.contactId = 42
            user.bhv = false
            user.study = "Computer Science"
            user.startStudyYear = 2020
            user.email = "Test@Example.com "

            val found = persistAndReload(user, User::class.java) { it.id }

            assertEquals(user.getUsername(), found.getUsername())
            assertEquals(user.getPassword(), found.getPassword())
            assertEquals(user.firstName, found.firstName)
            assertEquals(user.lastName, found.lastName)
            assertEquals(user.prefix, found.prefix)
            assertEquals(user.initials, found.initials)
            assertEquals(user.phoneNumber, found.phoneNumber)
            assertEquals(user.studentNumber, found.studentNumber)
            assertEquals(user.dateOfBirth, found.dateOfBirth)
            assertEquals(user.discord, found.discord)
            assertEquals(user.steamid, found.steamid)
            assertEquals(user.newsletter, found.newsletter)
            assertEquals(user.enabled, found.enabled)
            assertEquals(user.consentPrivacy, found.consentPrivacy)
            assertEquals(user.consentGdpr, found.consentGdpr)
            assertEquals(user.gender, found.gender)
            assertEquals(user.photoConsent, found.photoConsent)
            assertEquals(user.nationality, found.nationality)
            assertEquals(user.roles, found.roles)
            assertEquals(user.ehbo, found.ehbo)
            assertEquals(user.contactId, found.contactId)
            assertEquals(user.bhv, found.bhv)
            assertEquals(user.study, found.study)
            assertEquals(user.startStudyYear, found.startStudyYear)
            assertEquals("test@example.com", found.email)
        }

        @Test
        fun `persists address relation when setting entity`() {
            val address = persist(addressFactory.createBasic())
            val user = userFactory.createBasic()
            user.address = address

            val found = persistAndReload(user, User::class.java) { it.id }

            assertEquals(address.id, found.addressId)
            assertEquals(address.id, found.address?.id)
        }

        @Test
        fun `persists address relation when setting id`() {
            val address = persist(addressFactory.createBasic())
            val user = userFactory.createBasic()
            user.addressId = address.id

            val found = persistAndReload(user, User::class.java) { it.id }

            assertEquals(address.id, found.addressId)
            assertEquals(address.id, found.address?.id)
        }
    }
}
