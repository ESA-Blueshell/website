package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable

/**
 * The single paint-job row, id 1.
 *
 * One row, not one per anything: there is one canvas and one image on it. The migration seeds it,
 * so it always exists; an admin edits it in place. [prefix] is null until an admin sets one, which
 * is what keeps the pinger idle. [imagePath] is a stored file's path, not a foreign key, because
 * files soft-delete and this only needs the path the public URL is built from.
 */
@Entity
@Table(name = "pinger_paint")
class PingerPaint(
    @Id
    @Column(name = "id", nullable = false)
    // The one paint-job row; the migration seeds it at id 1.
    override val id: Long = 1L,
    @Column(name = "prefix", length = 64)
    var prefix: String? = null,
    @Column(name = "rate_pps", nullable = false)
    var ratePps: Int,
    @Column(name = "origin_x", nullable = false)
    var originX: Int,
    @Column(name = "origin_y", nullable = false)
    var originY: Int,
    @Column(name = "width", nullable = false)
    var width: Int,
    @Column(name = "height", nullable = false)
    var height: Int,
    @Column(name = "image_path", length = 255)
    var imagePath: String? = null,
) : Identifiable<Long>
