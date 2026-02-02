package net.blueshell.api.validation.file

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [AllowedContentTypesValidator::class])
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class AllowedContentTypes(
    vararg val value: String,
    val message: String = "Unsupported media type. Allowed: {value}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload?>> = []
)

