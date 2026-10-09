package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable

/**
 * The single paint-job settings row, id 1.
 *
 * One row, not one per anything: there is one canvas, one prefix and one rate. The images and their
 * boxes live in [PingerPlacement], one row each, so the canvas can carry several at once. The
 * migration seeds this row, so it always exists; an admin edits it in place. [prefix] is null until
 * an admin sets one, which is what keeps the pinger idle. [siteCieEnabled] gates the always-on
 * SiteCie painter; [ratePps] is also its rate, since SiteCie is that painter. [defaultsInitialized]
 * is set once, when the bootstrap first lays down the default placement, so an admin edit survives
 * every restart.
 */
@Entity
@Table(name = "pinger_paint")
class PingerPaint(
    @Id
    @Column(name = "id", nullable = false)
    override val id: Long = 1L,
    @Column(name = "prefix", length = 64)
    var prefix: String? = null,
    @Column(name = "rate_pps", nullable = false)
    var ratePps: Int,
    @Column(name = "site_cie_enabled", nullable = false)
    var siteCieEnabled: Boolean = true,
    @Column(name = "defaults_initialized", nullable = false)
    var defaultsInitialized: Boolean = false,
) : Identifiable<Long>
