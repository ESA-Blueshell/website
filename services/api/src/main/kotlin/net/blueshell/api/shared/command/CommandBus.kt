package net.blueshell.api.shared.command

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.stereotype.Component

@Component
class CommandBus(
    handlers: List<CommandHandler<*, *>>,
    private val validator: Validator
) {
    private val handlersByType = handlers.associateBy { it.commandType }

    @Suppress("UNCHECKED_CAST")
    fun <R, C : Command<R>> dispatch(command: C): R {
        val violations = validator.validate(command)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }
        val handler = handlersByType[command::class]
            ?: throw IllegalArgumentException("No handler registered for ${command::class.qualifiedName}")
        return (handler as CommandHandler<C, R>).handle(command)
    }
}
