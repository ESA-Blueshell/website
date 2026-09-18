package net.blueshell.api.shared.seed

import net.blueshell.api.board.domain.ShippedBoardArtOnStartup
import net.blueshell.api.board.domain.ShippedBoardsOnStartup
import net.blueshell.api.esports.domain.ShippedArtOnStartup
import net.blueshell.api.esports.domain.ShippedEsportsOnStartup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.Order
import kotlin.reflect.jvm.javaMethod

/**
 * Spring resolves an @EventListener's order from the method, never the declaring class, so an
 * @Order sitting on the class is ignored and the art can run before the records it needs.
 */
class SeedStartupOrderTest {
    private fun orderOf(type: Class<*>): Int? {
        val method = type.declaredMethods.single { it.name == "onReady" }
        return AnnotatedElementUtils.findMergedAnnotation(method, Order::class.java)?.value
    }

    @Test
    fun `every seed listener carries its order on the method`() {
        listOf(
            ShippedBoardsOnStartup::class.java,
            ShippedEsportsOnStartup::class.java,
            ShippedBoardArtOnStartup::class.java,
            ShippedArtOnStartup::class.java,
        ).forEach { type ->
            assertThat(orderOf(type))
                .describedAs("%s.onReady must carry @Order; on the class it is ignored", type.simpleName)
                .isNotNull()
        }
    }

    @Test
    fun `records run before the art that hangs on them`() {
        assertThat(SeedOrder.RECORDS).isLessThan(SeedOrder.ART)
        assertThat(orderOf(ShippedBoardsOnStartup::class.java)).isLessThan(orderOf(ShippedBoardArtOnStartup::class.java))
        assertThat(orderOf(ShippedEsportsOnStartup::class.java)).isLessThan(orderOf(ShippedArtOnStartup::class.java))
    }
}
