package net.blueshell.api.vote.domain

import net.blueshell.api.vote.persistence.TrackSource

/** IGDB's image host, and the thumbnail hosts of the services a track can come from. */
fun isPictureHost(host: String): Boolean = host == "images.igdb.com" || TrackSource.entries.any { it.service.isThumbnailHost(host) }
