package net.blueshell.api.vote.domain

private val IGDB_IMAGE_ID = Regex("^[a-z0-9]{1,40}$")

/** IGDB's address for the cover [imageId] at [size], or null for an id IGDB would never issue. */
fun igdbCoverUrl(
    imageId: String,
    size: String,
): String? = if (IGDB_IMAGE_ID.matches(imageId)) "https://images.igdb.com/igdb/image/upload/$size/$imageId.jpg" else null

/** IGDB's image host, YouTube's thumbnail host and Spotify's image hosts. */
fun isPictureHost(host: String): Boolean = host in setOf("images.igdb.com", "i.ytimg.com", "i.scdn.co") || host.endsWith(".spotifycdn.com")
