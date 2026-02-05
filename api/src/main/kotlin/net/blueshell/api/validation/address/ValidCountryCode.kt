package net.blueshell.api.validation.address

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [_root_ide_package_.net.blueshell.api.validation.address.CountryCodeValidator::class])
annotation class ValidCountryCode(
    val message: String = "Country must be a valid ISO 3166-1 alpha-2 code",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
