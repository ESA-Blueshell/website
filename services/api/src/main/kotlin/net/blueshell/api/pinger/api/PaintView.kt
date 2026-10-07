package net.blueshell.api.pinger.api

/**
 * The paint job as anyone reads it: the prefix, the rate, the box on the canvas and the public URL
 * of the image, already resolved from its stored path. A path, not an absolute URL — the caller
 * resolves it against the api origin, as it does every other public file.
 *
 * [siteCieEnabled] gates the always-on SiteCie painter; [ratePps] is also its rate, since SiteCie
 * is that painter.
 */
data class PaintView(
    val prefix: String?,
    val ratePps: Int,
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
    val imageUrl: String?,
    val siteCieEnabled: Boolean,
)
