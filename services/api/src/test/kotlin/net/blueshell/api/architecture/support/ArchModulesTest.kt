package net.blueshell.api.architecture.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArchModulesTest {
    @Test
    fun `every direct sub-package is a module, except the application root`() {
        assertThat(ArchModules.moduleOf("net.blueshell.api.game.persistence")).isEqualTo("game")
        assertThat(ArchModules.moduleOf("net.blueshell.api.anything.new")).isEqualTo("anything")
        assertThat(ArchModules.moduleOf("net.blueshell.api.platform.config")).isNull()
        assertThat(ArchModules.moduleOf("net.blueshell.api")).isNull()
        assertThat(ArchModules.moduleOf("org.springframework.data")).isNull()
    }

    private fun reaches(
        from: String,
        to: String,
        type: String,
    ) = ArchModules.reachesForeignRepository("net.blueshell.api.$from", "net.blueshell.api.$to", type)

    @Test
    fun `a module reaching another module's repository is caught`() {
        assertThat(reaches("esports.domain", "game.persistence", "GameRepository")).isTrue()
        assertThat(reaches("platform.config", "user.persistence", "UserRepository")).isTrue()
    }

    @Test
    fun `its own repositories, another module's entities and anything outside a persistence package pass`() {
        assertThat(reaches("game.api", "game.persistence", "GameRepository")).isFalse()
        assertThat(reaches("esports.domain", "game.persistence", "Game")).isFalse()
        assertThat(reaches("esports.domain", "shared.repository", "BaseRepository")).isFalse()
    }
}
