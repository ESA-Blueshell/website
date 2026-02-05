package net.blueshell.api.common.util

import java.security.SecureRandom
import java.util.function.BiConsumer

object MappingUtil {
    private const val CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*()-_=+<>?"
    private const val PASSWORD_LENGTH = 12
    private val random = SecureRandom()


    fun <O, T> applyIfFieldIsNotNull(user: O?, obj: T?, applier: BiConsumer<O?, T?>) {
        if (obj != null) {
            applier.accept(user, obj)
        }
    }

    fun generateRandomString(): String {
        val password = StringBuilder(PASSWORD_LENGTH)
        for (i in 0..<PASSWORD_LENGTH) {
            val index = random.nextInt(CHAR_SET.length)
            password.append(CHAR_SET[index])
        }
        return password.toString()
    }
}
