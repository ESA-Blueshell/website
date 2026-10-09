package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity

/**
 * One image and the box it lands in on the 3840x2160 canvas. Several rows paint several images at
 * once, each moved and resized on its own.
 *
 * [imagePath] is a stored file's path, not a foreign key, because files soft-delete and this only
 * needs the path the public URL is built from. [ordinal] fixes the draw order, lowest first.
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
) : AutoIdEntity()
