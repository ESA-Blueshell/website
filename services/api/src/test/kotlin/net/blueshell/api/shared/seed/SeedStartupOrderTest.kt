package net.blueshell.api.shared.seed

import net.blueshell.api.board.domain.ShippedBoardArt
import net.blueshell.api.board.domain.ShippedBoardsOnStartup
import net.blueshell.api.esports.domain.ShippedEsportsOnStartup
import net.blueshell.api.esports.domain.ShippedTeamArt
import net.blueshell.api.game.domain.ShippedGameArt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.Order

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
            ShippedBoardArt::class.java,
            ShippedTeamArt::class.java,
            ShippedGameArt::class.java,
        ).forEach { type ->
            assertThat(orderOf(type))
                .describedAs("%s.onReady must carry @Order; on the class it is ignored", type.simpleName)
                .isNotNull()
        }
    }

    @Test
    fun `records run before the art that hangs on them`() {
        assertThat(SeedOrder.RECORDS).isLessThan(SeedOrder.ART)
        assertThat(orderOf(ShippedBoardsOnStartup::class.java)).isLessThan(orderOf(ShippedBoardArt::class.java))
        assertThat(orderOf(ShippedEsportsOnStartup::class.java)).isLessThan(orderOf(ShippedTeamArt::class.java))
        // The esports seed is what adds the games their art hangs on.
        assertThat(orderOf(ShippedEsportsOnStartup::class.java)).isLessThan(orderOf(ShippedGameArt::class.java))
    }
}
