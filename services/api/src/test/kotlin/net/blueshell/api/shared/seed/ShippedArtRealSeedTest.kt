package net.blueshell.api.shared.seed

import net.blueshell.api.esports.domain.EsportsSeed
import net.blueshell.api.game.domain.GameSeed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The art the site actually ships, held to the rules its siblings prove.
 *
 * Those two run on fixture seeds, so nothing there would notice a row in the real files naming a picture
 * nobody committed, or a 4K master somebody dropped in. This is the guard on the real directories, and the
 * one test here that is meant to fail when the data moves.
 */
class ShippedArtRealSeedTest {
    @ParameterizedTest
    @MethodSource("inventories")
    fun `every picture the shipped seed names is committed, and every committed picture is named`(inventory: ShippedArtInventory) {
        assertThat(inventory.named).isNotEmpty()
        assertThat(inventory.missing())
            .describedAs("art a seed file names but nobody committed under %s", inventory.directory)
            .isEmpty()
        assertThat(inventory.orphaned())
            .describedAs(
                "art committed under %s that no row names; bind it or hold it in gameart/",
                inventory.directory,
            ).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("inventories")
    fun `no shipped picture is wider or taller than 1440p`(inventory: ShippedArtInventory) {
        assertThat(inventory.unreadable())
            .describedAs("shipped art whose size could not be read")
            .isEmpty()
        assertThat(inventory.oversized(ShippedArtInventory.MAX_WIDTH, ShippedArtInventory.MAX_HEIGHT))
            .describedAs(
                "shipped art over %dx%d; fit it inside that box",
                ShippedArtInventory.MAX_WIDTH,
                ShippedArtInventory.MAX_HEIGHT,
            ).isEmpty()
    }

    companion object {
        @JvmStatic
        fun inventories() =
            listOf(
                ShippedArtInventory(EsportsSeed.files, "src/main/resources", "teams.csv" to "banner"),
                ShippedArtInventory(GameSeed.files, "src/main/resources", "banners.csv" to "banner", "icons.csv" to "icon"),
            )
    }
}
