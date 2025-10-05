package net.blueshell.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for EventController.
 * - Creates a committee first (required by POST /events)
 * - Verifies create, list, update, delete flows
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("brevo-mock")
class EventControllerIT extends UserTestSupport {

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

    private Map<String, Object> committeePayload() {
        return Map.of(
                "name", "VakanCie",
                "description", "Committee for events and drinks",
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

    private Long createCommitteeAndReturnId() throws Exception {
        MvcResult createResult = mvc.perform(post("/committees")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(committeePayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        AdvancedCommitteeDTO dto = mapper.readValue(
                createResult.getResponse().getContentAsByteArray(),
                AdvancedCommitteeDTO.class
        );
        assertNotNull(dto.getId());
        return dto.getId();
    }

    private Map<String, Object> exampleEventPayload(Long committeeId) {
        // Building the payload equivalent to the user's example message
        Map<String, Object> signUpForm = new HashMap<>();
        List<Map<String, Object>> questions = new ArrayList<>();

        questions.add(new HashMap<>(Map.of(
                "type", "CHECKBOX",
                "label", "Checkboxes",
                "idx", 0,
                "choiceLabels", List.of("Check a", "Check b")
        )));
        questions.add(new HashMap<>(Map.of(
                "type", "DESCRIPTION",
                "label", "Description",
                "idx",  1
        )));
        questions.add(new HashMap<>(Map.of(
                "type", "OPEN",
                "label", "Open question",
                "idx", 2
        )));
        questions.add(new HashMap<>(Map.of(
                "type", "RADIO",
                "label", "Radio question",
                "idx", 3,
                "choiceLabels", List.of("a", "b", "c")
        )));

        signUpForm.put("questions", questions);

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "New Event");
        payload.put("location", "Esports Lounge Twente");
        payload.put("description", "The best description");
        payload.put("startTime", "2025-10-05T19:00:00.000+02:00");
        payload.put("endTime", "2025-10-05T22:00:00.000+02:00");
        payload.put("memberPrice", 0);
        payload.put("publicPrice", 0);
        payload.put("visible", true);
        payload.put("membersOnly", true);
        payload.put("signUp", true);
        payload.put("committeeId", committeeId);
        payload.put("signUpForm", signUpForm);

        return payload;
    }

    private EventDTO createEvent(Long committeeId) throws Exception {
        Map<String, Object> payload = exampleEventPayload(committeeId);

        MvcResult result = mvc.perform(post("/events")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.committeeId").value(committeeId.intValue()))
                .andExpect(jsonPath("$.title").value("New Event"))
                .andExpect(jsonPath("$.location").value("Esports Lounge Twente"))
                .andExpect(jsonPath("$.description").value("The best description"))
                .andExpect(jsonPath("$.startTime").value(containsString("2025-10-05T19:00:00+02:00")))
                .andExpect(jsonPath("$.endTime").value(containsString("2025-10-05T22:00:00+02:00")))
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.membersOnly").value(true))
                .andExpect(jsonPath("$.signUp").value(true))
                .andExpect(jsonPath("$.signUpForm.questions", hasSize(4)))
                .andReturn();

        return mapper.readValue(result.getResponse().getContentAsByteArray(), EventDTO.class);
    }

    @Test
    void eventsAreCreatedCorrectly() throws Exception {
        Long committeeId = createCommitteeAndReturnId();
        EventDTO created = createEvent(committeeId);
        assertNotNull(created.getId());
    }

    @Test
    void fetchingEventsWorks() throws Exception {
        Long committeeId = createCommitteeAndReturnId();
        createEvent(committeeId);

        mvc.perform(get("/events").with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }


    @Test
    void updatesEvent() throws Exception {
        Long committeeId = createCommitteeAndReturnId();
        EventDTO dto = createEvent(committeeId);

        mvc.perform(put("/events/{id}", dto.getId())
                        .with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().is4xxClientError());

        dto.getSignUpForm().getQuestions().remove(
                dto.getSignUpForm().getQuestions().stream()
                        .skip(3)
                        .findFirst()
                        .orElse(null)
        );
        dto.setTitle("Updated Event");
        dto.setLocation("Updated Location");
        dto.setDescription("Updated Description");
        dto.setApproved(false);
        dto.setSignUp(false);

        mvc.perform(put("/events/{id}", dto.getId())
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId().toString()))
                .andExpect(jsonPath("$.committeeId").value(committeeId.intValue()))
                .andExpect(jsonPath("$.title").value("Updated Event"))
                .andExpect(jsonPath("$.location").value("Updated Location"))
                .andExpect(jsonPath("$.description").value("Updated Description"))
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.signUp").value(false))
                .andExpect(jsonPath("$.signUpForm.questions", hasSize(3)))
                .andExpect(jsonPath("$.signUpForm.questions[*].idx",
                        containsInAnyOrder(0, 1, 2)))
                .andReturn();
    }

    @Test
    void deletingEventsRemovesThem() throws Exception {
        Long committeeId = createCommitteeAndReturnId();
        EventDTO created = createEvent(committeeId);

        mvc.perform(get("/events").with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mvc.perform(delete("/events/{id}", created.getId())
                        .with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/events").with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }
}

