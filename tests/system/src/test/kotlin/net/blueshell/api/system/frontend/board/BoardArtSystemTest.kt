package net.blueshell.api.system.frontend.board

import net.blueshell.systemtests.PlaywrightTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * The photographs the repository ships reach a browser, at the widths they are stored at.
 *
 * Nowhere else can answer this. The api's integration tests prove the pictures are stored and
 * that the payload names them, and never see a browser fetch one; the frontend's end-to-end
 * suite runs against mocked routes and so never sees the shipped photographs at all. What is
 * left between the two is the whole delivery: a repeatable seed that wrote the boards, a boot
 * loader that stored the art, an endpoint that serves the bytes, a payload that names the
 * copies and a browser that fetches one of them and decodes it.
 *
 * Every failure in that gap is quiet. A payload whose copies went missing still names a
 * picture, and the picture still draws — at several times the weight, on every screen, for
 * ever. So these tests assert the numbers rather than that an image appeared.
 *
 * Driven from the api's own origin rather than the frontend's. The page a reader will see is
 * #930's to build, so there is no board page drawing these yet; what is being proved is that
 * the bytes are really there and that a real browser picks a stored width out of the ladder the
 * api published. Being on the api's origin also means no request here is cross-origin, so a
 * CORS rule cannot be what makes this pass or fail.
 */
@Tag("system")
class BoardArtSystemTest : PlaywrightTestBase() {

    @Test
    fun `a seeded board photograph is stored, served and drawn`() {
        val photo = drawn(PHOTOGRAPH)

        // Served by the api out of storage, rather than bundled into the frontend: the whole
        // point of the boot loader is that the art arrives without a deploy.
        assertThat(photo.src).startsWith("/files/public/board-photos/")

        // The copies, which is the list a page composes its `srcset` out of. A single-entry
        // srcset would mean every screen gets the master.
        assertThat(photo.candidates)
            .describedAs("widths offered for a shipped board photograph")
            .hasSizeGreaterThanOrEqualTo(3)
        assertThat(photo.candidates).contains(320)
        assertThat(photo.candidates.max())
            .describedAs("the widest copy offered, which says the photograph is high-resolution")
            .isGreaterThanOrEqualTo(960)

        // And the bytes are really there. A url that resolves to nothing still sets a src.
        assertThat(photo.decoded).describedAs("the photograph decoded").isTrue()

        // The copy actually fetched is one of the narrow ones, not the master. This is the
        // saving: a band asked for 400 pixels of picture must not be sent 2560 of them.
        assertThat(photo.chosen).describedAs("the copy the browser fetched").matches(".*-\\d+\\.webp")
        assertThat(photo.chosenWidth)
            .describedAs("the width fetched, against the widest offered")
            .isNotNull()
            .isLessThan(photo.candidates.max())
        // And the bytes behind that address are as wide as its name claims. A copy named
        // `-640.webp` holding 320 pixels is served, decodes and is blurry for ever.
        assertThat(photo.fetchedWidth)
            .describedAs("the fetched copy's own pixel width, against the width its name claims")
            .isEqualTo(photo.chosenWidth)
    }

    @Test
    fun `a seeded portrait is stored, served and drawn at a portrait's own widths`() {
        val portrait = drawn(PORTRAIT)

        // Its own directory, because a portrait is its own kind of picture with its own ladder.
        assertThat(portrait.src).startsWith("/files/public/board-portraits/")
        assertThat(portrait.decoded).describedAs("the portrait decoded").isTrue()

        // The ladder a portrait is stored at, which stops far below a photograph's. A portrait
        // offered 1920 would be the whole reason the kind exists undone.
        assertThat(portrait.candidates).contains(160)
        assertThat(portrait.candidates.max())
            .describedAs("the widest copy offered for a portrait")
            .isLessThanOrEqualTo(640)
        assertThat(portrait.chosenWidth).isNotNull().isLessThanOrEqualTo(640)
    }

    /**
     * One shipped picture, drawn.
     *
     * The whole read happens in the page: it asks the api which boards there are, picks the
     * first picture of the kind asked for, hangs an `img` on the document with the widths the
     * api published, and waits for the browser to decode whichever copy it chose. `decode()`
     * rather than the `complete` flag, because a browser reports an image complete the moment
     * it gives up on it.
     */
    private fun drawn(kind: String): Picture {
        page.navigate("$apiUrl/boards")

        @Suppress("UNCHECKED_CAST")
        val read = page.evaluate(
            """
            async (kind) => {
              const boards = await (await fetch('/boards')).json()
              const picture = kind === 'photo'
                ? boards.map(board => board.photo).find(Boolean)
                : boards.flatMap(board => board.members.map(one => one.portrait)).find(Boolean)
              if (!picture) return {missing: kind}

              const img = document.createElement('img')
              img.src = picture.url
              if (picture.renditions.length > 0) {
                img.srcset = picture.renditions.map(one => one.url + ' ' + one.width + 'w').join(', ')
                img.sizes = '400px'
              }
              document.body.appendChild(img)
              let decoded = true
              try { await img.decode() } catch (e) { decoded = false }

              // `naturalWidth` on an img with a `w` srcset is density-corrected: it reports the
              // width in css pixels, which is the `sizes` above. A plain img reports the copy's own.
              const bytes = document.createElement('img')
              bytes.src = img.currentSrc
              document.body.appendChild(bytes)
              try { await bytes.decode() } catch (e) { decoded = false }

              return {
                src: picture.url,
                width: picture.width,
                height: picture.height,
                candidates: picture.renditions.map(one => one.width),
                chosen: img.currentSrc,
                fetchedWidth: bytes.naturalWidth,
                decoded,
              }
            }
            """.trimIndent(),
            kind,
        ) as Map<String, Any?>

        val missing = read["missing"]
        check(missing == null) { "No board in the seeded history carries a $missing" }

        return Picture(
            src = read["src"] as String,
            width = (read["width"] as Number?)?.toInt(),
            height = (read["height"] as Number?)?.toInt(),
            candidates = (read["candidates"] as List<*>).map { (it as Number).toInt() },
            chosen = read["chosen"] as String,
            fetchedWidth = (read["fetchedWidth"] as Number).toInt(),
            decoded = read["decoded"] == true,
        )
    }

    private data class Picture(
        val src: String,
        val width: Int?,
        val height: Int?,
        val candidates: List<Int>,
        val chosen: String,
        val fetchedWidth: Int,
        val decoded: Boolean,
    ) {
        /** The width of the copy the browser fetched, taken from its address. */
        val chosenWidth: Int? get() = Regex("-(\\d+)\\.webp").find(chosen)?.groupValues?.get(1)?.toInt()
    }

    private companion object {
        const val PHOTOGRAPH = "photo"
        const val PORTRAIT = "portrait"
    }
}
