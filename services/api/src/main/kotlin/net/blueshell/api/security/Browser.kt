package net.blueshell.api.security

/**
 * The browser family and operating system a User-Agent names, never their versions, so an update
 * does not look like another browser.
 */
data class Browser(
    val family: String,
    val platform: String,
) {
    companion object {
        val UNKNOWN = Browser("Unknown browser", "unknown system")

        fun of(userAgent: String?): Browser {
            val ua = userAgent?.takeIf { it.isNotBlank() } ?: return UNKNOWN
            return Browser(familyOf(ua), platformOf(ua))
        }

        private fun familyOf(ua: String): String =
            when {
                "Edg/" in ua || "EdgA/" in ua || "EdgiOS/" in ua -> "Edge"
                "OPR/" in ua || "Opera" in ua -> "Opera"
                "Firefox/" in ua || "FxiOS/" in ua -> "Firefox"
                "Chrome/" in ua || "CriOS/" in ua || "Chromium/" in ua -> "Chrome"
                "Safari/" in ua -> "Safari"
                else -> UNKNOWN.family
            }

        private fun platformOf(ua: String): String =
            when {
                "Windows" in ua -> "Windows"
                "Android" in ua -> "Android"
                "iPhone" in ua || "iPad" in ua || "iPod" in ua -> "iOS"
                "CrOS" in ua -> "ChromeOS"
                "Mac OS X" in ua || "Macintosh" in ua -> "macOS"
                "Linux" in ua -> "Linux"
                else -> UNKNOWN.platform
            }
    }
}
