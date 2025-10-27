package net.blueshell.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO;
import net.blueshell.api.dto.event.EventDTO;
import net.blueshell.api.model.User;
import net.blueshell.api.testsupport.UserTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Commit;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static java.lang.Thread.sleep;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
class CommitteeControllerIT extends UserTestSupport {

    private final Map<Role, User> userMap = new EnumMap<>(Role.class);
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        userMap.put(Role.MEMBER, createUserWithRole(Role.MEMBER));
        userMap.put(Role.BOARD, createUserWithRole(Role.BOARD));
    }

    private Map<String, Object> examplePayload() {
        return Map.of(
                "name", "Test Committee",
                "description", "A test committee for integration tests",
                "members", List.of(
                        Map.of(
                                "role", "Chair",
                                "userId", userMap.get(Role.BOARD).getId()
                        ),
                        Map.of(
                                "role", "Member",
                                "userId", userMap.get(Role.MEMBER).getId()
                        )
                )
        );
    }

    @Test
    void committeesAreCreatedCorrectly() throws Exception {
        var board = userMap.get(Role.BOARD);
        var member = userMap.get(Role.MEMBER);

        mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test Committee"))
                .andExpect(jsonPath("$.description").value("A test committee for integration tests"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[*].role",
                        containsInAnyOrder("Chair", "Member")))
                .andExpect(jsonPath("$.members[*].userId",
                        containsInAnyOrder(board.getId().intValue(), member.getId().intValue())));

        // Refresh entities
        member = refreshUser(member);
        board = refreshUser(board);

        // Verify roles were properly assigned by the AFTER_COMMIT listeners
        assertTrue(member.hasRole(Role.COMMITTEE));
        assertTrue(board.hasRole(Role.COMMITTEE));
    }

    @Test
    void fetchingCommitteesWorks() throws Exception {
        // create one
        mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isCreated());

        // fetch all
        mvc.perform(get("/committees").with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void fetchingCommitteeByIdWorks() throws Exception {
        // create
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isCreated())
                .andReturn();

        AdvancedCommitteeDTO created = mapper.readValue(
                createResult.getResponse().getContentAsByteArray(),
                AdvancedCommitteeDTO.class
        );
        assertNotNull(created.getId());

        // fetch by id
        mvc.perform(get("/committees/{id}", created.getId())
                        .with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.name").value("Test Committee"))
                .andExpect(jsonPath("$.members", hasSize(2)));
    }

    private AdvancedCommitteeDTO createCommittee() throws Exception {
        MvcResult result = mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readValue(result.getResponse().getContentAsByteArray(), AdvancedCommitteeDTO.class);
    }

    @Test
    void updatingCommitteesRemovesMembers() throws Exception {
        // initial create
        AdvancedCommitteeDTO dto = createCommittee();

        // prepare updated payload (switch to a single member, using the existing MEMBER account)
        var board = userMap.get(Role.BOARD);
        var member = userMap.get(Role.MEMBER);

        dto.getMembers().remove(1);
        dto.setName("Updated Committee Name");
        dto.setDescription("Updated description text");
        dto.getMembers().get(0).setRole("No longer chair");

        // perform update
        mvc.perform(put("/committees/{id}", dto.getId())
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated Committee Name"))
                .andExpect(jsonPath("$.description").value("Updated description text"))
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.members[0].role").value("No longer chair"))
                .andExpect(jsonPath("$.members[0].userId").value(board.getId().intValue()));

        assertFalse(refreshUser(member).hasRole(Role.COMMITTEE));
        assertTrue(refreshUser(board).hasRole(Role.COMMITTEE));
    }

    @Test
    void updatingCommitteesUpdatesMembers() throws Exception {
        // initial create
        AdvancedCommitteeDTO dto = createCommittee();

        // prepare updated payload (switch to a single member, using the existing MEMBER account)
        var board = userMap.get(Role.BOARD);
        var member = userMap.get(Role.MEMBER);

        dto.setName("Updated Committee Name");
        dto.setDescription("Updated description text");
        dto.getMembers().get(0).setRole("No longer chair");
        dto.getMembers().get(1).setRole("No longer member");

        // perform update
        mvc.perform(put("/committees/{id}", dto.getId())
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated Committee Name"))
                .andExpect(jsonPath("$.description").value("Updated description text"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].role").value("No longer chair"))
                .andExpect(jsonPath("$.members[0].userId").value(board.getId().intValue()))
                .andExpect(jsonPath("$.members[1].role").value("No longer member"))
                .andExpect(jsonPath("$.members[1].userId").value(member.getId().intValue()));

        assertTrue(refreshUser(board).hasRole(Role.COMMITTEE));
    }

    @Test
    void deletingCommitteesRemovesThem() throws Exception {
        // create
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isCreated())
                .andReturn();

        AdvancedCommitteeDTO created = mapper.readValue(
                createResult.getResponse().getContentAsByteArray(),
                AdvancedCommitteeDTO.class
        );

        // delete
        mvc.perform(delete("/committees/{id}", created.getId())
                        .with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isNoContent());

        // ensure gone
        mvc.perform(get("/committees").with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/committees/{id}", created.getId())
                        .with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isNotFound());
    }
}