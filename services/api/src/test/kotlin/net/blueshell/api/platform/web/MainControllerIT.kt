package net.blueshell.api.platform.web

import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class MainControllerIT : UserTestSupport() {

    @Nested
    inner class HealthCheck {
        @Test
        fun `returns healthy status`() {
            mvc.perform(get("/health"))
                .andExpect(status().isOk)
                .andExpect(content().string("true"))
        }
    }
}
