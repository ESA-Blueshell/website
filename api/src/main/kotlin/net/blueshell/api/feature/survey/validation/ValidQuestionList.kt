package net.blueshell.api.feature.survey.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidQuestionListValidator::class])
annotation class ValidQuestionList(
    val message: String = "Invalid list of questions",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

