package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.pinger.domain.MotionMode
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/**
 * One image and the box it lands in on the 3840x2160 canvas. Several rows paint several images at
 * once, each moved and resized on its own.
 *
 * [imagePath] is a stored file's path, not a foreign key, because files soft-delete and this only
 * needs the path the public URL is built from. [ordinal] fixes the draw order, lowest first. The box
 * is where the image sits at [motionEpoch]; from then on it follows [motionMode] at
 * ([motionVx], [motionVy]) px/s, as net.blueshell.api.pinger.domain.CanvasMotion computes.
 */
@Entity
@Table(name = "pinger_placement")
class PingerPlacement(
    @Column(name = "image_path", nullable = false, length = 255)
    var imagePath: String,
    @Column(name = "origin_x", nullable = false)
    var originX: Int,
    @Column(name = "origin_y", nullable = false)
    var originY: Int,
    @Column(name = "width", nullable = false)
    var width: Int,
    @Column(name = "height", nullable = false)
    var height: Int,
    @Column(name = "ordinal", nullable = false)
    var ordinal: Int = 0,
    @Column(name = "motion_epoch", nullable = false)
    var motionEpoch: Instant,
    @Convert(converter = MotionModeConverter::class)
    @Column(name = "motion_mode", nullable = false, length = 16)
    var motionMode: MotionMode = MotionMode.STATIC,
    @Column(name = "motion_vx", nullable = false)
    var motionVx: Double = 0.0,
    @Column(name = "motion_vy", nullable = false)
    var motionVy: Double = 0.0,
) : AutoIdEntity()
