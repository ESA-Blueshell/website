package net.blueshell.api.pinger.api

import net.blueshell.api.file.api.FileService
import net.blueshell.api.pinger.persistence.PingerPaint
import net.blueshell.api.pinger.persistence.PingerPaintRepository
import net.blueshell.api.pinger.persistence.PingerPlacement
import net.blueshell.api.pinger.persistence.PingerPlacementRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.seed.SeedOrder
import net.blueshell.api.user.api.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit

/** The paint-job row the migration seeds at id 1; the bootstrap only ever reads this one. */
private const val PAINT_ROW_ID = 1L

/** A documentation prefix, so the out-of-the-box default targets nothing real until an admin sets one. */
private const val DEFAULT_PREFIX = "2001:db8:5747:5055::/64"

/** The shipped Blueshell logo, read off the classpath and stored as a public PINGER_PAINT file on
 * first boot. A transparent-background PNG, so the lossless webp the store converts it to keeps the
 * alpha and the logo sits on whatever the canvas band shows behind it. */
private const val DEFAULT_IMAGE_RESOURCE = "seed/pinger/blueshell-logo.png"
private const val DEFAULT_IMAGE_NAME = "blueshell-logo.png"
private const val DEFAULT_IMAGE_MEDIA_TYPE = "image/png"

/** The account the shipped image is credited to, as all shipped art is. */
private const val SITE_ACCOUNT = "system"

/** The box the default placement lands in on the 3840x2160 canvas, matching the logo's shape. */
private const val DEFAULT_ORIGIN_X = 1470
private const val DEFAULT_ORIGIN_Y = 180
private const val DEFAULT_WIDTH = 900
private const val DEFAULT_HEIGHT = 720

/**
 * Lays the default image, prefix and box onto the one paint job exactly once, ever, in any
 * environment.
 *
 * Runs on every start but writes on only the first: the row carries a persistent
 * [PingerPaint.defaultsInitialized] marker the bootstrap sets the one time it stores the image, so
 * an admin who later clears the image from `/management/pinger` is never re-seeded, and every admin
 * edit survives the next restart. Framework-agnostic of profile on purpose — out of the box the
 * canvas already renders something, which is the one state the page is built to make inviting. A
 * failure to store the image is logged and skipped rather than failing the start: the box is still
 * seeded, the canvas is simply idle until an admin uploads one.
 */
@Component
class PingerPaintDefaults(
    private val paints: PingerPaintRepository,
    private val placements: PingerPlacementRepository,
    private val files: FileService,
    private val users: UserService,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Order(SeedOrder.ART)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            apply()
        } catch (e: Exception) {
            log.warn("[pinger-paint-defaults] the default paint job could not be laid down: {}", e.message)
        }
    }

    @Transactional
    fun apply() {
        val row = paints.findById(PAINT_ROW_ID).orElse(null) ?: return
        if (row.defaultsInitialized) return

        row.prefix = DEFAULT_PREFIX
        storeDefaultImage()?.let { image ->
            placements.save(
                PingerPlacement(
                    imagePath = image.path,
                    originX = DEFAULT_ORIGIN_X,
                    originY = DEFAULT_ORIGIN_Y,
                    width = DEFAULT_WIDTH,
                    height = DEFAULT_HEIGHT,
                    ordinal = 0,
                    motionEpoch = clock.instant().truncatedTo(ChronoUnit.MILLIS),
                ),
            )
        }
        row.defaultsInitialized = true
        paints.save(row)
        log.info("[pinger-paint-defaults] laid down the default prefix and the Blueshell logo placement")
    }

    /** The shipped image, stored as a public file the one time it is needed, or null if it cannot be. */
    private fun storeDefaultImage(): net.blueshell.api.file.persistence.File? {
        val owner = runCatching { users.findByUsername(SITE_ACCOUNT) }.getOrNull()
        if (owner == null) {
            log.warn("[pinger-paint-defaults] no '{}' account to credit the default image to", SITE_ACCOUNT)
            return null
        }
        val bytes = defaultImageStream() ?: error("Shipped image $DEFAULT_IMAGE_RESOURCE is missing")
        return files.store(bytes, DEFAULT_IMAGE_NAME, DEFAULT_IMAGE_MEDIA_TYPE, FileType.PINGER_PAINT, owner)
    }

    internal fun defaultImageStream(): java.io.InputStream? = javaClass.classLoader.getResourceAsStream(DEFAULT_IMAGE_RESOURCE)

    private companion object {
        val log = LoggerFactory.getLogger(PingerPaintDefaults::class.java)
    }
}
