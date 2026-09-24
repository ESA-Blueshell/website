package net.blueshell.api.event.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class EventPingedRolesIT : UserTestSupport() {
    @Test
    fun `an event keeps the Discord roles it pings, and says them back`() {
        val board = createUserWithRole(Role.BOARD)
        val committee = createCommitteeFixture()
        val body =
            """
            {"committeeId": ${committee.id}, "title": "LAN party", "description": "Bring a rig.", "location": "Pakhuis",
             "startTime": "2026-10-10T18:00:00Z", "endTime": "2026-10-10T21:00:00Z", "approved": true,
             "membersOnly": false, "signUp": false,
             "pingedRoles": [{"id": "1144058844004233369", "name": "Gamers"}, {"id": "1144058844004233370", "name": "Alumni"}]}
            """.trimIndent()

        val created =
            mvc
                .perform(post("/events").with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.pingedRoles[0].name").value("Alumni"))
                .andReturn()
        val id = com.jayway.jsonpath.JsonPath.read<Int>(created.response.contentAsString, "$.id")

        mvc
            .perform(get("/events/{id}", id).with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pingedRoles.length()").value(2))
            .andExpect(jsonPath("$.pingedRoles[1].id").value("1144058844004233369"))
    }

    @Test
    fun `refuses a pinged role that is not a Discord ID`() {
        val board = createUserWithRole(Role.BOARD)
        val committee = createCommitteeFixture()
        val body =
            """
            {"committeeId": ${committee.id}, "title": "LAN party", "description": "Bring a rig.",
             "startTime": "2026-10-10T18:00:00Z", "endTime": "2026-10-10T21:00:00Z", "approved": true,
             "membersOnly": false, "signUp": false, "pingedRoles": [{"id": "everyone", "name": "@everyone"}]}
            """.trimIndent()

        mvc
            .perform(post("/events").with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
    }
}
