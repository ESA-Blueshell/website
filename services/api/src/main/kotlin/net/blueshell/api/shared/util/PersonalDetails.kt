package net.blueshell.api.shared.util

/**
 * Takes personal details out of text bound for a log line or a recorded exception.
 *
 * The database driver and JSON parse errors quote the value they refused, so a refusal on a unique
 * email or phone number would carry that value; the quotes are emptied and the constraint or type
 * named beside them stays. Anything shaped like an email address or an IBAN is masked wherever it is.
 */
object PersonalDetails {
    private val quotedValues =
        listOf(
            Regex("""(Duplicate entry ')[^']*(')"""),
            Regex("""(Incorrect \w+ value: ')[^']*(')"""),
            Regex("""(from String ")[^"]*(")"""),
            Regex("""(String value \(")[^"]*("\))"""),
            Regex("""(Unrecognized token ')[^']*(')"""),
        )
    private val emailAddress = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
    private val iban = Regex("""\b[A-Z]{2}\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\b""")

    fun scrub(text: String): String {
        val emptied = quotedValues.fold(text) { result, quoted -> quoted.replace(result, "$1[value]$2") }
        return iban.replace(emailAddress.replace(emptied, "[email]"), "[iban]")
    }
}
