package net.blueshell.api.platform.web.oidc

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Verifies the per-host role gating that Traefik forwardAuth delegates
 * to. Each test sends a GET to `/oauth2/forward-auth` with the
 * `X-Forwarded-Host` (and `X-Forwarded-Uri`) headers Traefik would set;
 * the controller is expected to return either:
 *  - 200 with X-User-Id / X-User-Groups (authenticated + role OK), or
 *  - 302 to /login (anonymous), or
 *  - 302 to /unauthorized (authenticated but role too low).
 */
@SpringBootTest
class ForwardAuthControllerIT : UserTestSupport() {

    @Nested
    inner class Anonymous {
        @Test
        fun `redirects anonymous caller to login with the original URL preserved as redirect query`() {
            mvc.perform(
                get("/oauth2/forward-auth")
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "vault.esa-blueshell.nl")
                    .header("X-Forwarded-Uri", "/ui/dashboard")
            )
                .andExpect(status().isFound)
                .andExpect(redirectedUrlPattern("https://v2.esa-blueshell.nl/login?redirect=*"))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("vault.esa-blueshell.nl%2Fui%2Fdashboard")))
        }

        @Test
        fun `unknown host falls back to ADMIN required and still redirects anonymous to login`() {
            mvc.perform(
                get("/oauth2/forward-auth")
                    .header("X-Forwarded-Host", "rogue.example.com")
                    .header("X-Forwarded-Uri", "/")
            )
                .andExpect(status().isFound)
                .andExpect(redirectedUrlPattern("https://v2.esa-blueshell.nl/login?redirect=*"))
        }
    }

    @Nested
    inner class WrongRole {
        @Test
        fun `member hitting vault is redirected to unauthorized with the service in the query`() {
            val member = createUserWithRole(Role.MEMBER)
            mvc.perform(
                get("/oauth2/forward-auth")
                    .with(bearer(member))
                    .header("X-Forwarded-Host", "vault.esa-blueshell.nl")
                    .header("X-Forwarded-Uri", "/")
            )
                .andExpect(status().isFound)
                .andExpect(header().string(HttpHeaders.LOCATION, "https://v2.esa-blueshell.nl/unauthorized?service=vault.esa-blueshell.nl"))
        }

        @Test
        fun `board hitting vault is redirected to unauthorized — board does not inherit admin`() {
            val board = createUserWithRole(Role.BOARD)
            mvc.perform(
                get("/oauth2/forward-auth")
                    .with(bearer(board))
                    .header("X-Forwarded-Host", "vault.esa-blueshell.nl")
                    .header("X-Forwarded-Uri", "/")
            )
                .andExpect(status().isFound)
                .andExpect(header().string(HttpHeaders.LOCATION, "https://v2.esa-blueshell.nl/unauthorized?service=vault.esa-blueshell.nl"))
        }
    }

    @Nested
    inner class Allowed {
        @Test
        fun `admin hitting vault gets 200 with user headers`() {
            val admin = createUserWithRole(Role.ADMIN)
            mvc.perform(
                get("/oauth2/forward-auth")
                    .with(bearer(admin))
                    .header("X-Forwarded-Host", "vault.esa-blueshell.nl")
            )
                .andExpect(status().isOk)
                .andExpect(header().string("X-User-Id", admin.id!!.toString()))
                .andExpect(header().string("X-User-Groups", org.hamcrest.Matchers.containsString("ADMIN")))
        }

        @Test
        fun `board hitting listmonk gets 200 (listmonk requires only board)`() {
            val board = createUserWithRole(Role.BOARD)
            mvc.perform(
                get("/oauth2/forward-auth")
                    .with(bearer(board))
                    .header("X-Forwarded-Host", "listmonk.esa-blueshell.nl")
            )
                .andExpect(status().isOk)
                .andExpect(header().string("X-User-Id", board.id!!.toString()))
                .andExpect(header().string("X-User-Groups", org.hamcrest.Matchers.containsString("BOARD")))
        }

        @Test
        fun `admin hitting listmonk gets 200 — admin inherits board`() {
            val admin = createUserWithRole(Role.ADMIN)
            mvc.perform(
                get("/oauth2/forward-auth")
                    .with(bearer(admin))
                    .header("X-Forwarded-Host", "listmonk.esa-blueshell.nl")
            )
                .andExpect(status().isOk)
        }
    }
}
