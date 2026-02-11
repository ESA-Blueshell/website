package net.blueshell.api.domain.survey.web.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidAnswerValidator::class])
annotation class ValidAnswer(
    val message: String = "Invalid answer to question",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)