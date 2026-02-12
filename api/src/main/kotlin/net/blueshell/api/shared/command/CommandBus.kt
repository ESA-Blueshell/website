package net.blueshell.api.shared.command

import org.springframework.stereotype.Component

@Component
class CommandBus(
    handlers: List<CommandHandler<*, *>>
) {
    private val handlersByType = handlers.associateBy { it.commandType }

    @Suppress("UNCHECKED_CAST")
    fun <R, C : Command<R>> dispatch(command: C): R {
        val handler = handlersByType[command::class]
            ?: throw IllegalArgumentException("No handler registered for ${command::class.qualifiedName}")
        return (handler as CommandHandler<C, R>).handle(command)
    }
}
