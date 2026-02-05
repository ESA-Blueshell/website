package net.blueshell.api.controller;

import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO;
import net.blueshell.api.factory.UnifiedFactory;
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory;
import net.blueshell.api.model.User;
import net.blueshell.api.testsupport.UserTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.EnumMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for CommitteeController endpoints and role side-effects.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommitteeControllerIT extends UserTestSupport {

    private final Map<Role, User> users = new EnumMap<>(Role.class);

    private final UnifiedFactory uf;
    private final AdvancedCommitteeDTOFactory committeeDTOFactory;

    @Autowired
    CommitteeControllerIT(UnifiedFactory uf, AdvancedCommitteeDTOFactory committeeDTOFactory) {
        this.uf = uf;
        this.committeeDTOFactory = committeeDTOFactory;
    }

    @BeforeEach
    void setupUsers() {
        users.put(Role.MEMBER, createUserWithRole(Role.MEMBER));
        users.put(Role.BOARD, createUserWithRole(Role.BOARD));
    }

    /** Build a standard committee payload with two members. */
    private AdvancedCommitteeDTO committeePayload() {
        var board = users.get(Role.BOARD);
        var member = users.get(Role.MEMBER);

        var dto = committeeDTOFactory.createWithMemberRoles("Chair", "Member");
        dto.setName("Test Committee");
        dto.setDescription("A test committee for integration tests");
        dto.getMembers().get(0).setUserId(board.getId());
        dto.getMembers().get(1).setUserId(member.getId());
        return dto;
    }

    /** Create a committee and return it. */
    private AdvancedCommitteeDTO givenCommitteeCreated() throws Exception {
        var res = mvc.perform(post("/committees")
                        .with(bearer(users.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(committeePayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        return mapper.readValue(res.getResponse().getContentAsByteArray(), AdvancedCommitteeDTO.class);
    }

    @Test
    void committeesAreCreatedCorrectly() throws Exception {
        var board = users.get(Role.BOARD);
        var member = users.get(Role.MEMBER);

        mvc.perform(post("/committees")
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(committeePayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test Committee"))
                .andExpect(jsonPath("$.description").value("A test committee for integration tests"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[*].role", containsInAnyOrder("Chair", "Member")))
                .andExpect(jsonPath("$.members[*].userId",
                        containsInAnyOrder(board.getId().intValue(), member.getId().intValue())));

        assertTrue(refreshUser(member).hasRole(Role.COMMITTEE));
        assertTrue(refreshUser(board).hasRole(Role.COMMITTEE));
    }

    @Test
    void fetchingCommitteesWorks() throws Exception {
        givenCommitteeCreated();

        mvc.perform(get("/committees").with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void fetchingCommitteeByIdWorks() throws Exception {
        AdvancedCommitteeDTO created = givenCommitteeCreated();

        mvc.perform(get("/committees/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().intValue()))
                .andExpect(jsonPath("$.name").value("Test Committee"))
                .andExpect(jsonPath("$.members", hasSize(2)));
    }

    @Test
    void updatingCommitteesRemovesMembers() throws Exception {
        AdvancedCommitteeDTO dto = givenCommitteeCreated();
        var board = users.get(Role.BOARD);
        var member = users.get(Role.MEMBER);

        dto.getMembers().remove(1);
        dto.setName("Updated Committee Name");
        dto.setDescription("Updated description text");
        dto.getMembers().get(0).setRole("No longer chair");

        mvc.perform(put("/committees/{id}", dto.getId())
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId().intValue()))
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
        AdvancedCommitteeDTO dto = givenCommitteeCreated();
        var board = users.get(Role.BOARD);
        var member = users.get(Role.MEMBER);

        dto.setName("Updated Committee Name");
        dto.setDescription("Updated description text");
        dto.getMembers().get(0).setRole("No longer chair");
        dto.getMembers().get(1).setRole("No longer member");

        mvc.perform(put("/committees/{id}", dto.getId())
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Committee Name"))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].userId").value(board.getId().intValue()))
                .andExpect(jsonPath("$.members[1].userId").value(member.getId().intValue()));

        assertTrue(refreshUser(board).hasRole(Role.COMMITTEE));
    }

    @Test
    void deletingCommitteesRemovesThem() throws Exception {
        AdvancedCommitteeDTO created = givenCommitteeCreated();

        mvc.perform(delete("/committees/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/committees").with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/committees/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isNotFound());
    }
}
