package net.blueshell.api.auth.domain

/** Where somebody locked out, or worried, is sent for help. */
data class SecurityContacts(
    val email: String,
    val boardChannel: String,
    val suggestionsChannel: String,
) {
    val markdown: String
        get() =
            "email the board at [$email](mailto:$email), or ask in " +
                "[#board-questions]($boardChannel) or [#sitecie]($suggestionsChannel) on our Discord"
}
