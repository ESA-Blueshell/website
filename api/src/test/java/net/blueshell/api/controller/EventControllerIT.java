package net.blueshell.api.controller;

import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.dto.event.EventBannerDTO;
import net.blueshell.api.dto.event.EventDTO;
import net.blueshell.api.factory.UnifiedFactory;
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory;
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory;
import net.blueshell.api.model.User;
import net.blueshell.api.testsupport.UserTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.EnumMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for EventController endpoints (CRUD and listing).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventControllerIT extends UserTestSupport {

    private final Map<Role, User> users = new EnumMap<>(Role.class);

    private final UnifiedFactory uf;
    private final AdvancedCommitteeDTOFactory committeeDTOFactory;
    private final SurveyDTOFactory surveyFactory;

    @Autowired
    EventControllerIT(
            UnifiedFactory uf,
            AdvancedCommitteeDTOFactory committeeDTOFactory,
            SurveyDTOFactory surveyFactory
    ) {
        this.uf = uf;
        this.committeeDTOFactory = committeeDTOFactory;
        this.surveyFactory = surveyFactory;
    }

    @BeforeEach
    void setupUsers() {
        users.put(Role.MEMBER, createUserWithRole(Role.MEMBER));
        users.put(Role.BOARD, createUserWithRole(Role.BOARD));
    }

    /** Create a committee for Event ownership assertions. */
    private Long givenCommitteeId() throws Exception {
        var board = users.get(Role.BOARD);
        var member = users.get(Role.MEMBER);

        var committee = committeeDTOFactory.createWithMemberRoles("Chair", "Member");
        committee.setName("VakanCie");
        committee.setDescription("Committee for events and drinks");
        committee.getMembers().get(0).setUserId(board.getId());
        committee.getMembers().get(1).setUserId(member.getId());

        var res = mvc.perform(post("/committees")
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(committee)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        return mapper.readTree(res.getResponse().getContentAsByteArray()).path("id").asLong();
    }

    /** Upload a minimal banner image as the board user. */
    private FileDTO givenUploadedBannerAsBoard() throws Exception {
        var board = users.get(Role.BOARD);
        byte[] imageBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        var banner = new MockMultipartFile("file", "banner.jpg", "image/jpeg", imageBytes);

        var res = mvc.perform(multipart("/events/banners").file(banner).with(bearer(board)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readValue(res.getResponse().getContentAsByteArray(), FileDTO.class);
    }

    /** Create an Event with a banner and a form. */
    private EventDTO givenEventCreated(Long committeeId) throws Exception {
        var board = users.get(Role.BOARD);

        var savedFile = givenUploadedBannerAsBoard();
        var banner = uf.with(EventBannerDTO.class, b -> b.setFile(savedFile));

        EventDTO payload = uf.with(EventDTO.class, e -> {
            e.setCommitteeId(committeeId);
            e.setTitle("New Event");
            e.setLocation("Esports Lounge Twente");
            e.setDescription("The best description");
            e.setApproved(true);
            e.setMembersOnly(true);
            e.setSignUp(true);
            e.setBanner(banner);
            e.setSignUpForm(surveyFactory.createWithQuestionTypes(
                    QuestionType.DESCRIPTION, QuestionType.RADIO, QuestionType.CHECKBOX, QuestionType.OPEN
            ));
        });

        var res = mvc.perform(post("/events")
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.committeeId").value(committeeId.intValue()))
                .andExpect(jsonPath("$.title").value("New Event"))
                .andExpect(jsonPath("$.location").value("Esports Lounge Twente"))
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.membersOnly").value(true))
                .andExpect(jsonPath("$.signUp").value(true))
                .andExpect(jsonPath("$.signUpForm.questions", hasSize(4)))
                .andReturn();

        return mapper.readValue(res.getResponse().getContentAsByteArray(), EventDTO.class);
    }

    @Test
    void eventsAreCreatedCorrectly() throws Exception {
        Long committeeId = givenCommitteeId();
        EventDTO created = givenEventCreated(committeeId);
        assertNotNull(created.getId());
        assertNotNull(created.getSignUpForm());
    }

    @Test
    void fetchingEventsWorks() throws Exception {
        Long committeeId = givenCommitteeId();
        givenEventCreated(committeeId);

        mvc.perform(get("/events").with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void updatesEvent() throws Exception {
        Long committeeId = givenCommitteeId();
        EventDTO created = givenEventCreated(committeeId);

        mvc.perform(put("/events/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().is4xxClientError());

        EventDTO fresh = mapper.readValue(
                mvc.perform(get("/events/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsByteArray(),
                EventDTO.class);

        fresh.setTitle("Updated Event");
        fresh.setLocation("Updated Location");
        fresh.setDescription("Updated Description");
        fresh.setApproved(false);
        fresh.setSignUp(false);

        mvc.perform(put("/events/{id}", fresh.getId())
                        .with(bearer(users.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(fresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fresh.getId().intValue()))
                .andExpect(jsonPath("$.committeeId").value(committeeId.intValue()))
                .andExpect(jsonPath("$.title").value("Updated Event"))
                .andExpect(jsonPath("$.location").value("Updated Location"))
                .andExpect(jsonPath("$.description").value("Updated Description"))
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.signUp").value(false));
    }

    @Test
    void deletingEventsRemovesThem() throws Exception {
        Long committeeId = givenCommitteeId();
        EventDTO created = givenEventCreated(committeeId);

        mvc.perform(get("/events").with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mvc.perform(delete("/events/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/events").with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }
}
