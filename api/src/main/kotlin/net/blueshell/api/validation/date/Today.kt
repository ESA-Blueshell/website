package net.blueshell.api.validation.date

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TodayValidator::class])
annotation class Today(
    val message: String = "Date must be today",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload?>> = []
)

