package net.blueshell.api.contribution.domain

import java.text.Normalizer

/** Letters that do not decompose into a base letter and an accent, spelled as ING takes them. */
private val SPELLED =
    mapOf(
        'ß' to "ss",
        'æ' to "ae",
        'Æ' to "AE",
        'œ' to "oe",
        'Œ' to "OE",
        'ø' to "o",
        'Ø' to "O",
        'ł' to "l",
        'Ł' to "L",
        'đ' to "d",
        'Đ' to "D",
        'ı' to "i",
        'þ' to "th",
        'Þ' to "Th",
    )

private val TAKEN = Regex("[^A-Za-z0-9 /\\-?:().,'+]")

/**
 * Text as ING's incasso file takes it: letters, digits, space and / - ? : ( ) . , ' + only. An
 * accent is stripped from its letter rather than the letter dropped, so Zoë goes as Zoe; anything
 * else ING refuses is left out.
 */
fun ingText(raw: String): String {
    val spelled = raw.map { SPELLED[it] ?: it.toString() }.joinToString("")
    val bare = Normalizer.normalize(spelled, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    return bare.replace(TAKEN, "").replace(Regex(" {2,}"), " ").trim()
}
