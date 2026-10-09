package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.domain.MotionMode
import java.time.Instant

/**
 * The paint job as anyone reads it: the settings, and the placements that make up the canvas, each
 * already resolved to a public image URL and its box. A path, not an absolute URL — the caller
 * resolves it against the api origin, as it does every other public file.
 *
 * [siteCieEnabled] gates the always-on SiteCie painter; [ratePps] is also its rate, since SiteCie
 * is that painter. [serverTime] is the api's clock when the view was built, so a client can place a
 * moving box without trusting its own clock.
 */
data class PaintView(
    val prefix: String?,
    val ratePps: Int,
    val siteCieEnabled: Boolean,
    val placements: List<PlacementView>,
    val serverTime: Instant,
)

/**
 * One image on the canvas: its id, its public URL and the box it lands in on the 3840x2160 canvas
 * at [motionEpoch], from which it follows [motion].
 */
data class PlacementView(
    val id: Long,
    val imageUrl: String,
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
    val motion: PlacementMotion,
    val motionEpoch: Instant,
)

/** A placement's motion in px/s; a static one has no velocity. */
data class PlacementMotion(
    val mode: MotionMode,
    val vx: Double,
    val vy: Double,
)
