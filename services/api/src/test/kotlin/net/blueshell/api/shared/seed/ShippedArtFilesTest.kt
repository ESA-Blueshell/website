package net.blueshell.api.shared.seed

import net.blueshell.api.testsupport.EsportsSeedFixture
import net.blueshell.api.testsupport.GameSeedFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The art the seed files name and the art committed beside them are the same set.
 *
 * A row naming a picture nobody committed fails at start-up, on a running deployment, as a line in a log —
 * and the page it was meant for is simply drawn on yesterday's picture, which is not a visible failure. A
 * committed picture no row names is the opposite problem: this is publisher art in a public repository, so a
 * file that nothing uses is one nobody had a reason to publish. Both are build defects, so both fail the
 * build.
 *
 * The rule is proven here against the fixture seeds, whose files exist to be read in one screen. Whether
 * today's shipped art obeys it is a separate question, asked in [ShippedArtRealSeedTest].
 */
class ShippedArtFilesTest {
    @ParameterizedTest
    @MethodSource("inventories")
    fun `every picture the seed files name is on the classpath`(inventory: ShippedArtInventory) {
        assertThat(inventory.missing())
            .describedAs("art a seed file names but nobody committed under %s", inventory.directory)
            .isEmpty()
    }

    @ParameterizedTest
    @MethodSource("inventories")
    fun `every picture the repository ships is named by a seed file`(inventory: ShippedArtInventory) {
        assertThat(inventory.orphaned())
            .describedAs("art committed under %s that no row names", inventory.directory)
            .isEmpty()
    }

    @ParameterizedTest
    @MethodSource("inventories")
    fun `the files name art at all`(inventory: ShippedArtInventory) {
        // A guard on the two above, which both pass against a pair of files that name nothing.
        assertThat(inventory.named).isNotEmpty()
    }

    companion object {
        @JvmStatic
        fun inventories() =
            listOf(
                ShippedArtInventory(EsportsSeedFixture.files, "src/test/resources", "teams.csv" to "banner"),
                ShippedArtInventory(GameSeedFixture.files, "src/test/resources", "banners.csv" to "banner", "icons.csv" to "icon"),
            )
    }
}
