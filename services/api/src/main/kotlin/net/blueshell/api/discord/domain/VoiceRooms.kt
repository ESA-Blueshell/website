package net.blueshell.api.discord.domain

/** Somebody in a voice room, by the name and avatar the server shows for them. */
data class VoicePerson(
    val name: String,
    val avatar: String?,
)

/**
 * A voice room everybody can see. [locked] where everybody may see it but only some may join, so
 * the site can say a members' room is in use without offering a way in.
 */
data class VoiceRoom(
    val id: String,
    val name: String,
    val position: Int,
    val locked: Boolean,
    val people: List<VoicePerson>,
)

/**
 * The server as the gateway holds it: its name, the voice rooms everybody can see and, where the
 * application has the intent that keeps it current, how many are online ([online], Presence) and
 * how many are members ([members], Server Members). A count the gateway cannot keep is null.
 */
data class VoiceServer(
    val id: String,
    val name: String,
    val rooms: List<VoiceRoom>,
    val online: Int? = null,
    val members: Int? = null,
)

/** Where the live voice state comes from. Null until the gateway has the server. */
fun interface VoiceServerSource {
    fun server(): VoiceServer?

    /** Calls [listener] after anything the gateway receives, which may be many times a second. */
    fun onChange(listener: () -> Unit) {}
}

/** How many are on the server, and how many of them are online. */
data class GuildCounts(
    val members: Int,
    val online: Int,
)

/** Where the counts come from. Null where Discord would not say. */
fun interface GuildCountsSource {
    fun counts(): GuildCounts?
}
