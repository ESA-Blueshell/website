package net.blueshell.api.shared.command

import kotlin.reflect.KClass

interface CommandHandler<C : Command<R>, R> {
    val commandType: KClass<C>

    fun handle(command: C): R
}
