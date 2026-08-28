package net.blueshell.api.shared.util

private val CONTROL_CHARACTERS = Regex("\\p{Cntrl}")

/**
 * Renders an untrusted value safe to put in a log line (CWE-117): carriage returns, line
 * feeds and every other control character become `_`, so caller-supplied text cannot forge
 * extra log records. A null becomes `<null>`.
 *
 * The line-break pass deliberately goes through `java.lang.String.replaceAll` with the `\R`
 * line-break pattern. That is the shape the log-injection analyser recognises as a sanitiser;
 * Kotlin's own `String.replace` extensions compile to `kotlin.text.StringsKt`, which it does
 * not recognise, so an idiomatic Kotlin scrub reads as no scrub at all. The broader
 * control-character pass runs afterwards.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun sanitizeForLog(value: Any?): String {
    val raw = value?.toString() ?: return "<null>"
    val withoutLineBreaks = (raw as java.lang.String).replaceAll("\\R", "_")
    return withoutLineBreaks.replace(CONTROL_CHARACTERS, "_")
}
