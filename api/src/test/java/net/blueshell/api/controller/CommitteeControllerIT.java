package net.blueshell.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO;
import net.blueshell.api.model.User;
import net.blueshell.api.service.CommitteeMemberService;
import net.blueshell.api.service.CommitteeService;
import net.blueshell.api.testsupport.UserTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("brevo-mock")
class CommitteeControllerIT extends UserTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private final Map<Role, User> userMap = new EnumMap<>(Role.class);

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
    @Commit
    void committeesAreCreatedCorrectly() throws Exception {
        var board = userMap.get(Role.BOARD);
        var member = userMap.get(Role.MEMBER);

        mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test Committee"))
                .andExpect(jsonPath("$.description").value("A test committee for integration tests"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[*].role",
                        containsInAnyOrder("Chair", "Member")))
                .andExpect(jsonPath("$.members[*].user.id",
                        containsInAnyOrder(board.getId().intValue(), member.getId().intValue())));

        // Refresh entities to get updated state from database
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
                .andExpect(status().isOk());

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
                .andExpect(status().isOk())
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

    @Test
    void updatingCommitteesRemovesMembers() throws Exception {
        // initial create
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isOk())
                .andReturn();

        AdvancedCommitteeDTO created = mapper.readValue(
                createResult.getResponse().getContentAsByteArray(),
                AdvancedCommitteeDTO.class
        );

        // prepare updated payload (switch to a single member, using the existing MEMBER account)
        var board = userMap.get(Role.BOARD);
        var member = userMap.get(Role.MEMBER);

        Map<String, Object> updatedPayload = Map.of(
                "name", "Updated Committee Name",
                "description", "Updated description text",
                "members", List.of(
                        Map.of(
                                "id", refreshUser(board).getCommitteeMembers().stream().findFirst().get().getId(),
                                "role", "No longer chair",
                                "userId", board.getId()
                        )
                )
        );

        // perform update
        mvc.perform(put("/committees/{id}", created.getId())
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(updatedPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated Committee Name"))
                .andExpect(jsonPath("$.description").value("Updated description text"))
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.members[0].role").value("No longer chair"))
                .andExpect(jsonPath("$.members[0].user.id").value(board.getId().intValue()));

        assertFalse(refreshUser(member).hasRole(Role.COMMITTEE));
        assertTrue(refreshUser(board).hasRole(Role.COMMITTEE));
    }

    @Test
    void updatingCommitteesUpdatesMembers() throws Exception {
        // initial create
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isOk())
                .andReturn();

        AdvancedCommitteeDTO created = mapper.readValue(
                createResult.getResponse().getContentAsByteArray(),
                AdvancedCommitteeDTO.class
        );

        // prepare updated payload (switch to a single member, using the existing MEMBER account)
        var board = userMap.get(Role.BOARD);
        var member = userMap.get(Role.MEMBER);

        Map<String, Object> updatedPayload = Map.of(
                "name", "Updated Committee Name",
                "description", "Updated description text",
                "members", List.of(
                        Map.of(
                                "id", refreshUser(board).getCommitteeMembers().stream().findFirst().get().getId(),
                                "role", "No longer chair",
                                "userId", board.getId()
                        ),
                        Map.of(
                                "id", refreshUser(member).getCommitteeMembers().stream().findFirst().get().getId(),
                                "role", "No longer member",
                                "userId", member.getId()
                        )
                )
        );

        // perform update
        mvc.perform(put("/committees/{id}", created.getId())
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(updatedPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated Committee Name"))
                .andExpect(jsonPath("$.description").value("Updated description text"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].role").value("No longer chair"))
                .andExpect(jsonPath("$.members[0].user.id").value(board.getId().intValue()))
                .andExpect(jsonPath("$.members[1].role").value("No longer member"))
                .andExpect(jsonPath("$.members[1].user.id").value(member.getId().intValue()));

        assertTrue(refreshUser(board).hasRole(Role.COMMITTEE));
    }

    @Test
    void deletingCommitteesRemovesThem() throws Exception {
        // create
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload())))
                .andExpect(status().isOk())
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