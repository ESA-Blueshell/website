package net.blueshell.api.pinger.api

/**
 * A platform a member can download the desktop pinger for, and the installer asset the release
 * attaches for it.
 *
 * The names are fixed rather than versioned so a resolver matches them without knowing the tag,
 * and they are capitalised to stay clear of the lowercase `blueshell-pinger-*` helper binaries that
 * ride the same release.
 */
enum class PingerPlatform(
    val assetName: String,
) {
    MACOS("Blueshell-Pinger.dmg"),
    LINUX("Blueshell-Pinger-x86_64.AppImage"),
    WINDOWS("Blueshell-Pinger.exe"),
    ;

    companion object {
        /** The platform the `os` query value names, or null when it names none. */
        fun of(os: String): PingerPlatform? = entries.firstOrNull { it.name.equals(os, ignoreCase = true) }
    }
}
