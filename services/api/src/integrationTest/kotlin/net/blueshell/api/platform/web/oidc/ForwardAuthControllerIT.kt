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
 * the controller branches on the Accept header:
 *  - text/html (browser navigation) → 302 to /login or /unauthorized
 *  - anything else (SPA XHR) → 401 or 403 so the cross-origin auto-follow
 *    doesn't get CORS-blocked
 *  - authorised → 200 with X-User-Id / X-User-Groups
 */
@SpringBootTest
class ForwardAuthControllerIT : UserTestSupport() {

    @Nested
    inner class Anonymous {
        @Test
        fun `redirects anonymous HTML navigation to login with the original URL preserved as redirect query`() {
            mvc.perform(
                get("/oauth2/forward-auth")
                    .header(HttpHeaders.ACCEPT, "text/html")
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "vault.esa-blueshell.nl")
                    .header("X-Forwarded-Uri", "/ui/dashboard")
            )
                .andExpect(status().isFound)
                .andExpect(redirectedUrlPattern("https://v2.esa-blueshell.nl/login?redirect=*"))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("vault.esa-blueshell.nl%2Fui%2Fdashboard")))
        }

        @Test
        fun `anonymous XHR receives 401 with WWW-Authenticate instead of cross-origin redirect`() {
            mvc.perform(
                get("/oauth2/forward-auth")
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header("X-Forwarded-Host", "stalwart.esa-blueshell.nl")
                    .header("X-Forwarded-Uri", "/api/principal")
            )
                .andExpect(status().isUnauthorized)
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer realm=")))
        }

        @Test
        fun `unknown host with HTML accept falls back to ADMIN required and still redirects anonymous to login`() {
            mvc.perform(
                get("/oauth2/forward-auth")
                    .header(HttpHeaders.ACCEPT, "text/html")
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
        fun `member HTML navigation to vault is redirected to unauthorized with the service in the query`() {
            val member = createUserWithRole(Role.MEMBER)
            mvc.perform(
                get("/oauth2/forward-auth")
                    .with(bearer(member))
                    .header(HttpHeaders.ACCEPT, "text/html")
                    .header("X-Forwarded-Host", "vault.esa-blueshell.nl")
                    .header("X-Forwarded-Uri", "/")
            )
                .andExpect(status().isFound)
                .andExpect(header().string(HttpHeaders.LOCATION, "https://v2.esa-blueshell.nl/unauthorized?service=vault.esa-blueshell.nl"))
        }

        @Test
        fun `board HTML navigation to vault is redirected to unauthorized — board does not inherit admin`() {
            val board = createUserWithRole(Role.BOARD)
            mvc.perform(
                get("/oauth2/forward-auth")
                    .with(bearer(board))
                    .header(HttpHeaders.ACCEPT, "text/html")
                    .header("X-Forwarded-Host", "vault.esa-blueshell.nl")
                    .header("X-Forwarded-Uri", "/")
            )
                .andExpect(status().isFound)
                .andExpect(header().string(HttpHeaders.LOCATION, "https://v2.esa-blueshell.nl/unauthorized?service=vault.esa-blueshell.nl"))
        }

        @Test
        fun `member XHR on board-gated host receives 403, not redirect`() {
            val member = createUserWithRole(Role.MEMBER)
            mvc.perform(
                get("/oauth2/forward-auth")
                    .with(bearer(member))
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header("X-Forwarded-Host", "stalwart.esa-blueshell.nl")
            )
                .andExpect(status().isForbidden)
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
