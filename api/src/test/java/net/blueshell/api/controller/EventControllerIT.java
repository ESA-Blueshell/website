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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
                "idx", 3
        )));
        questions.add(new HashMap<>(Map.of(
                "type", "OPEN",
                "label", "Open question",
                "idx", 4
        )));
        questions.add(new HashMap<>(Map.of(
                "type", "RADIO",
                "label", "Radio question",
                "idx", 5,
                "choiceLabels", List.of("a", "b", "c")
        )));

        signUpForm.put("questions", questions);

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Announcement Drink");
        payload.put("location", "Esports Lounge Twente");
        payload.put("description",
                "Dear Member,\n\nTent Beer Mango here\nTent Beer Mango there \nDo you care? \nFor the Tent Beer and Mango might be fair.\nIs it here is it there?\nyou might find a bear!\nToday is not the day!\nBut a way  to know the way!\nWhat is this map?\nWhat kind of Pokémon, is this a trap?\nYou might get a kitty, dahm son that is litty.\nI like to go to Santo Domingo.\nIm kinda old might start a new bingo.\nVakanCie would like to invite you\nWe would be sad without you!\nSo will be seeing you on the 6th of October.\nYou might not be sober!\nFor we will be hosting an interest drink!\nTo reveal the picture we will give you wink.\n\n\nSo be at the Esport Lounge Twente\nPut in into your agenda\nIt will start at 19:00 sharp\nFeel free to bring Yannick some kwark\nGreetings VakanCie");
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
                .andExpect(jsonPath("$.title").value("Announcement Drink"))
                .andExpect(jsonPath("$.location").value("Esports Lounge Twente"))
                // Start/end times are mapped through LocalDateTime in the mapper; compare by prefix to avoid tz flakiness
                .andExpect(jsonPath("$.startTime").value(containsString("2025-10-05T19:00:00+02:00")))
                .andExpect(jsonPath("$.endTime").value(containsString("2025-10-05T22:00:00+02:00")))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.membersOnly").value(true))
                .andExpect(jsonPath("$.signUp").value(true))
                .andExpect(jsonPath("$.signUpForm.questions", hasSize(6)))
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
    void updatingEventsWorks() throws Exception {
        Long committeeId = createCommitteeAndReturnId();
        EventDTO created = createEvent(committeeId);

        Map<String, Object> updated = new HashMap<>(exampleEventPayload(committeeId));
        updated.put("title", "Updated Announcement");
        updated.put("description", "Updated description text");
        updated.put("visible", false);
        updated.put("signUp", false);

        mvc.perform(put("/events/{id}", created.getId())
                        .with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().is4xxClientError()); // ensure body missing triggers error

        mvc.perform(put("/events/{id}", created.getId())
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.committeeId").value(committeeId.intValue()))
                .andExpect(jsonPath("$.title").value("Updated Announcement"))
                .andExpect(jsonPath("$.description").value("Updated description text"))
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.signUp").value(false));
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

