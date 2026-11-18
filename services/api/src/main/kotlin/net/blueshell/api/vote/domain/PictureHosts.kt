package net.blueshell.api.vote.domain

/** IGDB's image host, YouTube's thumbnail host and Spotify's image hosts. */
fun isPictureHost(host: String): Boolean = host in setOf("images.igdb.com", "i.ytimg.com", "i.scdn.co") || host.endsWith(".spotifycdn.com")
