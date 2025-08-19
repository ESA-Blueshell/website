package net.blueshell.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.AdvancedCommitteeDTO;
import net.blueshell.api.service.CommitteeService;
import net.blueshell.api.testsupport.UserTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommitteeControllerIT extends UserTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    Map<String, Object> examplePayload = Map.of(
            "type", "AdvancedCommitteeDTO",
            "name", "Test Committee",
            "description", "A test committee for integration tests",
            "members", List.of(
                    Map.of(
                            "type", "CommitteeMemberDTO",
                            "role", "Chair",
                            "userId", 1
                    ),
                    Map.of(
                            "type", "CommitteeMemberDTO",
                            "role", "Member",
                            "userId", 2
                    )
            )
    );

    @Test
    void committeesAreCreatedCorrectly() throws Exception {
        mvc.perform(post("/committees")
                        .with(bearer(Role.BOARD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value(examplePayload.get("name")))
                .andExpect(jsonPath("$.description").value(examplePayload.get("description")))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[*].role",
                        containsInAnyOrder("Chair", "Member")))
                .andExpect(jsonPath("$.members[*].user.id",
                        containsInAnyOrder(1, 2)));
    }

    @Test
    void fetchingCommitteesWorks() throws Exception {
        // create one
        mvc.perform(post("/committees")
                        .with(bearer(Role.BOARD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isOk());

        // fetch all
        mvc.perform(get("/committees").with(bearer(Role.BOARD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void fetchingCommitteeByIdWorks() throws Exception {
        // create
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(Role.BOARD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isOk())
                .andReturn();

        AdvancedCommitteeDTO created = mapper.readValue(
                createResult.getResponse().getContentAsByteArray(),
                AdvancedCommitteeDTO.class
        );
        assertNotNull(created.getId());

        // fetch by id
        mvc.perform(get("/committees/{id}", created.getId())
                        .with(bearer(Role.BOARD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.name").value(examplePayload.get("name")))
                .andExpect(jsonPath("$.members", hasSize(2)));
    }

    @Test
    void updatingCommitteesUpdatesMembers() throws Exception {
        // initial create
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(Role.BOARD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isOk())
                .andReturn();

        AdvancedCommitteeDTO created = mapper.readValue(
                createResult.getResponse().getContentAsByteArray(),
                AdvancedCommitteeDTO.class
        );

        // prepare updated payload
        Map<String, Object> updatedPayload = Map.of(
                "type", "AdvancedCommitteeDTO",
                "name", "Updated Committee Name",
                "description", "Updated description text",
                "members", List.of(
                        Map.of(
                                "type", "CommitteeMemberDTO",
                                "role", "Lead",
                                "userId", 3
                        )
                )
        );

        // perform update
        mvc.perform(put("/committees/{id}", created.getId())
                        .with(bearer(Role.BOARD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(updatedPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated Committee Name"))
                .andExpect(jsonPath("$.description").value("Updated description text"))
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.members[0].role").value("Lead"))
                .andExpect(jsonPath("$.members[0].user.id").value(3));
    }

    @Test
    void deletingCommitteesRemovesThem() throws Exception {
        // create
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(Role.BOARD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isOk())
                .andReturn();

        AdvancedCommitteeDTO created = mapper.readValue(
                createResult.getResponse().getContentAsByteArray(),
                AdvancedCommitteeDTO.class
        );

        // delete
        mvc.perform(delete("/committees/{id}", created.getId())
                        .with(bearer(Role.BOARD)))
                .andExpect(status().isNoContent());

        // ensure gone
        mvc.perform(get("/committees").with(bearer(Role.BOARD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/committees/{id}", created.getId())
                        .with(bearer(Role.BOARD)))
                .andExpect(status().isNotFound());
    }
}
