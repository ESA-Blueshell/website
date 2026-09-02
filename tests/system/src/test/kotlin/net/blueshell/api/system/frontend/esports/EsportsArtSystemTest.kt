package net.blueshell.api.system.frontend.esports

import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.pollForValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * The art the repository ships reaches a page, at the widths it is stored at.
 *
 * Nowhere else can answer this. The frontend's own end-to-end suite runs against mocked
 * routes, so it proves the markup composes a `srcset` out of whatever the api says and never
 * sees the shipped pictures; the api's integration tests prove the pictures are stored and
 * that the payload names them, and never see a browser choose one. What is left between the
 * two is the whole delivery: a boot loader that stored the art, an endpoint that serves the
 * bytes, a payload that names the copies, and a browser that fetches one of them.
 *
 * Every failure in that gap is silent. A page whose `srcset` lost its copies still draws, and
 * still draws the right picture -- at several times the weight, on every screen, for ever. So
 * these tests assert the numbers rather than that an image appeared.
 */
@Tag("system")
class EsportsArtSystemTest : PlaywrightTestBase() {

    @Test
    fun `the index draws the shipped art, offered at the widths it is stored at`() {
        openIndex()

        val banner = bannerOfFirstSlice()

        // Served by the api out of storage, rather than bundled into the frontend: the whole
        // point of the boot loader is that the art arrives without a deploy.
        assertThat(banner.src).contains("/files/public/game-banners/")

        // The copies, which is the list the page composes its `srcset` out of. A single-entry
        // srcset would mean every screen gets the master.
        assertThat(banner.candidates)
            .describedAs("widths offered for the shipped banner")
            .hasSizeGreaterThanOrEqualTo(4)
        assertThat(banner.candidates).contains(320)

        // The shipped art is 1440p, so the ladder reaches the top of what a banner is stored
        // at. Asserted as a floor rather than exactly, because which game leads the band and
        // which picture it carries are both editorial.
        assertThat(banner.candidates.max())
            .describedAs("the widest copy offered, which says the art is high-resolution")
            .isGreaterThanOrEqualTo(1920)

        // And the bytes are really there. A url that resolves to nothing still sets a src.
        assertThat(banner.decoded).describedAs("the banner decoded").isTrue()
    }

    @Test
    fun `the browser is served a narrower copy than the picture it came from`() {
        openIndex()

        val banner = bannerOfFirstSlice()

        // What the page promised, worked out from how many slices share the row and how wide
        // the window is, rather than a fraction of the viewport.
        assertThat(banner.sizes).matches("\\d+px")

        // The copy actually fetched is one of the narrow ones, not the master. This is the
        // saving: the picture behind a slice is a fraction of the row, so serving 2560 pixels
        // of it would be most of them thrown away.
        assertThat(banner.chosen)
            .describedAs("the copy the browser fetched, which should be a stored width")
            .matches(".*-\\d+\\.webp")
        assertThat(banner.chosenWidth)
            .describedAs("the width fetched, against the widest offered")
            .isNotNull()
            .isLessThan(banner.candidates.max())
    }

    private fun openIndex() {
        page.navigate("$frontendUrl/esports/competitive-scene")
        page.locator("[data-testid='esports-game-slices']").first().waitFor()
        // Off the band, so the slice that opens is the one the page opens rather than whichever
        // the pointer happens to rest over.
        page.mouse().move(0.0, 0.0)
    }

    /**
     * The banner of the slice the page opens, once the page has settled on a width for it.
     *
     * Polled rather than read once: a banner is asked for a small copy first and for the width
     * it is really drawn at once that copy has arrived, so reading the attributes immediately
     * reads the first of the two passes.
     */
    private fun bannerOfFirstSlice(): Banner = pollForValue("the banner to settle on a width") {
        @Suppress("UNCHECKED_CAST")
        val read = page.evaluate(
            """
            () => {
              const el = document.querySelector('.slice__banner')
              if (!el || !el.currentSrc) return null
              const sizes = el.getAttribute('sizes') || ''
              if (!/^\d+px$/.test(sizes)) return null
              return {
                src: el.getAttribute('src') || '',
                sizes,
                chosen: el.currentSrc,
                candidates: (el.getAttribute('srcset') || '')
                  .split(',')
                  .map(one => parseInt(one.trim().split(/\s+/).pop(), 10))
                  .filter(one => !Number.isNaN(one)),
                complete: el.complete && el.naturalWidth > 0,
              }
            }
            """.trimIndent(),
        ) as Map<String, Any?>? ?: return@pollForValue null

        val candidates = (read["candidates"] as List<*>).map { (it as Number).toInt() }
        if (candidates.isEmpty() || read["complete"] != true) return@pollForValue null

        Banner(
            src = read["src"] as String,
            sizes = read["sizes"] as String,
            chosen = read["chosen"] as String,
            candidates = candidates,
            decoded = true,
        )
    }

    private data class Banner(
        val src: String,
        val sizes: String,
        val chosen: String,
        val candidates: List<Int>,
        val decoded: Boolean,
    ) {
        /** The width of the copy the browser fetched, taken from its address. */
        val chosenWidth: Int? get() = Regex("-(\\d+)\\.webp").find(chosen)?.groupValues?.get(1)?.toInt()
    }
}
