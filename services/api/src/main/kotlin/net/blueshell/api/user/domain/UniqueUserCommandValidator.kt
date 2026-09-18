package net.blueshell.api.user.domain

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * One field that has to be unique, and the two reads that answer whether it is taken.
 *
 * Two reads rather than one, because a new account and an edit ask different questions: an
 * edit must not collide with every row except the one it is editing.
 */
private class UniqueField(
    val property: String,
    val value: String?,
    val message: String,
    val isTaken: (String) -> Boolean,
    val isTakenByAnother: (String, Long) -> Boolean,
)

@Component
class UniqueUserCommandValidator
    @Autowired
    constructor(
        private val users: UserService,
    ) : ConstraintValidator<UniqueUserCommand, UserUniquenessCandidate> {
        override fun isValid(
            candidate: UserUniquenessCandidate?,
            context: ConstraintValidatorContext,
        ): Boolean {
            if (candidate == null) return true

            val subjectId = candidate.subjectId
            var valid = true

            for (field in uniqueFieldsOf(candidate)) {
                val value = field.value
                if (value.isNullOrBlank()) continue

                val taken =
                    if (subjectId == null) {
                        field.isTaken(value)
                    } else {
                        field.isTakenByAnother(value, subjectId)
                    }
                if (taken) {
                    valid = false
                    addViolation(context, field.property, field.message)
                }
            }

            return valid
        }

        private fun uniqueFieldsOf(candidate: UserUniquenessCandidate) =
            listOf(
                UniqueField(
                    "username",
                    candidate.username,
                    "Username is taken.",
                    users::existsByUsername,
                    users::existsByUsernameAndIdNot,
                ),
                UniqueField(
                    "email",
                    candidate.email,
                    "Email is taken.",
                    users::existsByEmail,
                    users::existsByEmailAndIdNot,
                ),
                UniqueField(
                    "discord",
                    candidate.discord,
                    "Discord is taken.",
                    users::existsByDiscord,
                    users::existsByDiscordAndIdNot,
                ),
                UniqueField(
                    "phoneNumber",
                    candidate.phoneNumber,
                    "Phone number is taken.",
                    users::existsByPhoneNumber,
                    users::existsByPhoneNumberAndIdNot,
                ),
            )

        private fun addViolation(
            context: ConstraintValidatorContext,
            property: String,
            message: String,
        ) {
            context.disableDefaultConstraintViolation()
            context
                .buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation()
        }
    }
