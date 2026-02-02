package net.blueshell.api.validation.survey

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidQuestionValidator::class])
annotation class ValidQuestion(
    val message: String = "Invalid question",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload?>> = []
)