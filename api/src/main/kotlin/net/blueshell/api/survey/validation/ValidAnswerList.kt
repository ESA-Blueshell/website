package net.blueshell.api.survey.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidAnswerListValidator::class])
annotation class ValidAnswerList(
    val message: String = "Invalid list of answers",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

