package net.blueshell.api.esports.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The art the site actually ships, held to the rules its siblings prove.
 *
 * Those two run on a fixture seed, so nothing there would notice a row in the real files naming a picture
 * nobody committed, or a 4K master somebody dropped in. This is the guard on the real directory, and the one
 * test in the esports suite that is meant to fail when the data moves.
 */
class ShippedArtRealSeedTest {

    private val inventory = ShippedArtInventory(EsportsSeed.files, "src/main/resources")

    @Test
    fun `every picture the shipped seed names is committed, and every committed picture is named`() {
        assertThat(inventory.named).isNotEmpty()
        assertThat(inventory.missing())
            .describedAs("art a seed file names but nobody committed under %s", inventory.directory)
            .isEmpty()
        assertThat(inventory.orphaned())
            .describedAs(
                "art committed under %s that no row names; bind it or hold it in gameart/",
                inventory.directory,
            )
            .isEmpty()
    }

    @Test
    fun `no shipped picture is wider or taller than 1440p`() {
        assertThat(inventory.unreadable())
            .describedAs("shipped art whose size could not be read")
            .isEmpty()
        assertThat(inventory.oversized(ShippedArtInventory.MAX_WIDTH, ShippedArtInventory.MAX_HEIGHT))
            .describedAs(
                "shipped art over %dx%d; fit it inside that box",
                ShippedArtInventory.MAX_WIDTH,
                ShippedArtInventory.MAX_HEIGHT,
            )
            .isEmpty()
    }
}
