package net.blueshell.api.esports.domain

import net.blueshell.api.testsupport.EsportsSeedFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The art the seed files name and the art committed beside them are the same set.
 *
 * A row naming a picture nobody committed fails at start-up, on a running deployment, as a line in a log —
 * and the page it was meant for is simply drawn on yesterday's picture, which is not a visible failure. A
 * committed picture no row names is the opposite problem: this is publisher art in a public repository, so a
 * file that nothing uses is one nobody had a reason to publish. Both are build defects, so both fail the
 * build.
 *
 * The rule is proven here against the fixture seed, whose files exist to be read in one screen. Whether
 * today's shipped art obeys it is a separate question, asked in [ShippedArtRealSeedTest].
 */
class ShippedArtFilesTest {

    private val inventory = ShippedArtInventory(EsportsSeedFixture.files, "src/test/resources")

    @Test
    fun `every picture the seed files name is on the classpath`() {
        assertThat(inventory.missing())
            .describedAs("art a seed file names but nobody committed under %s", inventory.directory)
            .isEmpty()
    }

    @Test
    fun `every picture the repository ships is named by a seed file`() {
        assertThat(inventory.orphaned())
            .describedAs("art committed under %s that no row names", inventory.directory)
            .isEmpty()
    }

    @Test
    fun `the files name art at all`() {
        // A guard on the two above, which both pass against a pair of files that name nothing.
        assertThat(inventory.named).isNotEmpty()
    }
}
