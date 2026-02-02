package net.blueshell.api.common.util

import java.security.SecureRandom
import java.util.*

object Util {
    const val ACTIVATION_KEY_LENGTH: Int = 30
    val ACTIVATION_VALID_SECONDS: Long = (3600 * 24 * 3 // 3 days
            ).toLong()
    private val random: Random = SecureRandom()

    fun getRandomCapitalString(length: Int): String {
        val randKey = StringBuilder()

        var options = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        for (i in 1..length) {
            options = options.uppercase(Locale.getDefault())
            randKey.append(options.get(getRandom(options.length - 1)))
        }

        return randKey.toString()
    }

    fun getRandom(inclBound: Int): Int {
        if (inclBound <= 0) {
            return 0
        }
        return random.nextInt(inclBound + 1)
    }
}
