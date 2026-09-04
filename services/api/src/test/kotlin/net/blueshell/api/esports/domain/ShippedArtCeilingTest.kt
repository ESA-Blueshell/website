package net.blueshell.api.esports.domain

import net.blueshell.api.testsupport.EsportsSeedFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The art a seed ships fits inside 1440p.
 *
 * A master wider than the widest width its kind is stored at is bytes that are only ever thrown away: every
 * copy a browser downloads is derived from it, and the ladder for a banner stops at 2560. So a 4K file in
 * here costs the repository, every clone and every image build, and buys a page nothing at all. Checked here
 * rather than written down somewhere, which is the kind of rule that holds until the next person adds a
 * picture: the failure is a page that weighs more than it should, which nobody notices by eye.
 *
 * The rule is proven against the fixture seed; [ShippedArtRealSeedTest] asks it of what the site ships.
 */
class ShippedArtCeilingTest {

    private val inventory = ShippedArtInventory(EsportsSeedFixture.files, "src/test/resources")

    @Test
    fun `no picture is wider or taller than 1440p`() {
        assertThat(inventory.oversized(ShippedArtInventory.MAX_WIDTH, ShippedArtInventory.MAX_HEIGHT))
            .describedAs(
                "art over %dx%d under %s; fit it inside that box",
                ShippedArtInventory.MAX_WIDTH,
                ShippedArtInventory.MAX_HEIGHT,
                inventory.directory,
            )
            .isEmpty()
    }

    /** A guard on the test above, which passes against a directory it failed to read. */
    @Test
    fun `there is art to measure, and every file of it could be read`() {
        assertThat(inventory.named).isNotEmpty()
        assertThat(inventory.unreadable())
            .describedAs("art whose size could not be read")
            .isEmpty()
    }
}
