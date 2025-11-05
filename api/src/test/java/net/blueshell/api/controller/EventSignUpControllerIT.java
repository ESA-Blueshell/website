package net.blueshell.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.FileDTO;
import net.blueshell.api.dto.GuestDTO;
import net.blueshell.api.dto.event.EventBannerDTO;
import net.blueshell.api.dto.event.EventDTO;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.factory.UnifiedFactory;
import net.blueshell.api.factory.dto.committee.AdvancedCommitteeDTOFactory;
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory;
import net.blueshell.api.model.User;
import net.blueshell.api.testsupport.UserTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests covering member and guest signup flows and filtering.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventSignUpControllerIT extends UserTestSupport {

    private final Map<Role, User> users = new EnumMap<>(Role.class);

    @Autowired
    private UnifiedFactory uf;
    @Autowired
    private AdvancedCommitteeDTOFactory committeeDTOFactory;
    @Autowired
    private SurveyDTOFactory surveyFactory;

    @BeforeEach
    void setupUsers() {
        users.put(Role.MEMBER, createUserWithRole(Role.MEMBER));
        users.put(Role.BOARD, createUserWithRole(Role.BOARD));
    }

    /**
     * Create a committee with 2 members: BOARD as chair and MEMBER as member.
     */
    private Long givenCommitteeId() throws Exception {
        var board = users.get(Role.BOARD);
        var member = users.get(Role.MEMBER);

        var advancedCommitteeDTO = committeeDTOFactory.createWithMemberRoles("Chair", "Member");
        advancedCommitteeDTO.setName("SignUp Committee");
        advancedCommitteeDTO.setDescription("Committee for signup testing");
        advancedCommitteeDTO.getMembers().get(0).setUserId(board.getId());
        advancedCommitteeDTO.getMembers().get(1).setUserId(member.getId());

        var res = mvc.perform(post("/committees")
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(advancedCommitteeDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = mapper.readTree(res.getResponse().getContentAsByteArray());
        return node.path("id").asLong();
    }

    /**
     * Upload an image banner as board member and return the FileDTO.
     */
    private FileDTO givenUploadedBannerAsBoard() throws Exception {
        var board = users.get(Role.BOARD);
        byte[] imageBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        var banner = new MockMultipartFile("file", "banner.jpg", "image/jpeg", imageBytes);

        var res = mvc.perform(multipart("/events/banners").file(banner).with(bearer(board)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readValue(res.getResponse().getContentAsByteArray(), FileDTO.class);
    }

    /**
     * Create an event with a simple survey form.
     */
    private EventDTO givenEventWithForm(Long committeeId) throws Exception {
        var board = users.get(Role.BOARD);

        var savedFile = givenUploadedBannerAsBoard();
        var banner = uf.with(EventBannerDTO.class, b -> b.setFile(savedFile));

        var eventDTO = uf.with(EventDTO.class, e -> {
            e.setCommitteeId(committeeId);
            e.setTitle("Signup Test Event");
            e.setApproved(true);
            e.setMembersOnly(false);
            e.setSignUp(true);
            e.setBanner(banner);
            e.setSignUpForm(surveyFactory.createWithQuestionTypes(
                    QuestionType.DESCRIPTION, QuestionType.RADIO, QuestionType.CHECKBOX, QuestionType.OPEN
            ));
        });

        var res = mvc.perform(post("/events")
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(eventDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.signUp").value(true))
                .andExpect(jsonPath("$.signUpForm.questions", hasSize(4)))
                .andReturn();

        return mapper.readValue(res.getResponse().getContentAsByteArray(), EventDTO.class);
    }

    /**
     * Build answers for the given event; 'variant' toggles create/update selection.
     */
    private List<AnswerDTO> answersFor(EventDTO event, String variant) {
        if (event.getSignUpForm() == null || event.getSignUpForm().getQuestions() == null) return List.of();

        var answers = new ArrayList<AnswerDTO>();
        event.getSignUpForm().getQuestions().forEach(q -> {
            if (q.getType() == QuestionType.DESCRIPTION) return;

            answers.add(uf.with(AnswerDTO.class, a -> {
                a.setQuestionId(q.getId());
                switch (q.getType()) {
                    case OPEN -> a.setTextResponse("create".equals(variant) ? "Initial response" : "Updated response");
                    case CHECKBOX -> {
                        int n = q.getChoiceLabels() == null ? 0 : q.getChoiceLabels().size();
                        a.setOptionSelections(IntStream.range(0, n)
                                .mapToObj(i -> "create".equals(variant) == (i % 2 == 0))
                                .toList());
                    }
                    case RADIO -> {
                        int n = q.getChoiceLabels() == null ? 0 : q.getChoiceLabels().size();
                        var picks = IntStream.range(0, n).mapToObj(i -> false).collect(Collectors.toList());
                        if (n > 0) picks.set("create".equals(variant) ? 0 : Math.min(1, n - 1), true);
                        a.setOptionSelections(picks);
                    }
                    default -> {
                    }
                }
            }));
        });
        return answers;
    }

    @Test
    void createListUpdateDeleteMemberFlowAndFilterEndpoints() throws Exception {
        var committeeId = givenCommitteeId();
        var event = givenEventWithForm(committeeId);

        var member = users.get(Role.MEMBER);
        var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
            es.setEventId(event.getId());
            es.setUserId(member.getId());
            es.setUser(uf.with(SimpleUserDTO.class, u -> {
                u.setId(member.getId());
                u.setVersion(member.getVersion());
            }));
            es.setAnswers(answersFor(event, "create"));
        });

        var createdRes = mvc.perform(post("/events/{eventId}/signups", event.getId())
                        .with(bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.answers", not(empty())))
                .andReturn();

        eventSignUpDTO = mapper.readValue(createdRes.getResponse().getContentAsByteArray(), EventSignUpDTO.class);
        long createdId = eventSignUpDTO.getId();

        mvc.perform(get("/events/{eventId}/signups", event.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventId", everyItem(is(event.getId().intValue()))))
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(get("/events/signups")
                        .queryParam("userId", String.valueOf(member.getId()))
                        .with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.id").value(member.getId().intValue()))
                .andExpect(jsonPath("$[0].eventId").value(event.getId().intValue()));

        eventSignUpDTO.setAnswers(answersFor(event, "update"));
        mvc.perform(put("/events/{eventId}/signups", event.getId())
                        .with(bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) createdId));

        mvc.perform(delete("/events/signups/{id}", createdId).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/events/{eventId}/signups", event.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void guestFlowCreateThenListByAccessTokenAndUpdateWithToken() throws Exception {
        var committeeId = givenCommitteeId();
        var event = givenEventWithForm(committeeId);
        var board = users.get(Role.BOARD);

        var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
            es.setEventId(event.getId());
            es.setAnswers(answersFor(event, "create"));
            es.setGuest(uf.with(GuestDTO.class, g -> {
                g.setEmail("guest.name@example.com");
                g.setName("Guesty McGuestface");
                g.setDiscord("Discord");
                g.setPhoneNumber("0611111111");
            }));
            es.setUser(null);
            es.setUserId(null);
        });

        var createdRes = mvc.perform(post("/events/{eventId}/signups", event.getId())
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        eventSignUpDTO = mapper.readValue(createdRes.getResponse().getContentAsByteArray(), EventSignUpDTO.class);
        String token = eventSignUpDTO.getGuest().getAccessToken();
        assertNotNull(token);

        mvc.perform(get("/events/signups/byAccessToken/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eventSignUpDTO.getId()))
                .andExpect(jsonPath("$[0].eventId").value(event.getId().intValue()));

        eventSignUpDTO.setAnswers(answersFor(event, "update"));
        eventSignUpDTO.getGuest().setName("Guesty Updated");
        eventSignUpDTO.getGuest().setAccessToken(token);

        mvc.perform(put("/events/{eventId}/signups", event.getId())
                        .queryParam("accessToken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().intValue()));

        mvc.perform(get("/events/{eventId}/signups", event.getId()).with(bearer(board)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Nested
    class CommitteeAndEvent {
        @Test
        void boardCanCreateCommitteeAndEventWithForm() throws Exception {
            var committeeId = givenCommitteeId();
            var event = givenEventWithForm(committeeId);
            assertNotNull(event.getId());
            assertNotNull(event.getSignUpForm());
            assertFalse(event.getSignUpForm().getQuestions().isEmpty());
        }
    }

    @Nested
    class MemberSignup {
        @Test
        void memberCanCreateSignup() throws Exception {
            var committeeId = givenCommitteeId();
            var event = givenEventWithForm(committeeId);
            var member = users.get(Role.MEMBER);

            var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
                es.setEventId(event.getId());
                es.setUser(uf.with(SimpleUserDTO.class, u -> {
                    u.setId(member.getId());
                    u.setVersion(member.getVersion());
                }));
                es.setAnswers(answersFor(event, "create"));
            });

            mvc.perform(post("/events/{eventId}/signups", event.getId())
                            .with(bearer(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventId").value(event.getId().intValue()));
        }

        @Test
        void memberCanUpdateOwnSignup() throws Exception {
            var committeeId = givenCommitteeId();
            var event = givenEventWithForm(committeeId);
            var member = users.get(Role.MEMBER);

            var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
                es.setEventId(event.getId());
                es.setUser(uf.with(SimpleUserDTO.class, u -> {
                    u.setId(member.getId());
                    u.setVersion(member.getVersion());
                }));
                es.setAnswers(answersFor(event, "create"));
            });

            var createdRes = mvc.perform(post("/events/{eventId}/signups", event.getId())
                            .with(bearer(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isCreated())
                    .andReturn();

            eventSignUpDTO = mapper.readValue(createdRes.getResponse().getContentAsByteArray(), EventSignUpDTO.class);
            eventSignUpDTO.setAnswers(answersFor(event, "update"));

            mvc.perform(put("/events/{eventId}/signups", event.getId())
                            .with(bearer(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(eventSignUpDTO.getId()));
        }

        @Test
        void boardCanFilterSignupsByUserAndEvent() throws Exception {
            var committeeId = givenCommitteeId();
            var event = givenEventWithForm(committeeId);
            var member = users.get(Role.MEMBER);
            var board = users.get(Role.BOARD);

            var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
                es.setEventId(event.getId());
                es.setUser(uf.with(SimpleUserDTO.class, u -> {
                    u.setId(member.getId());
                    u.setVersion(member.getVersion());
                }));
                es.setAnswers(answersFor(event, "create"));
            });

            mvc.perform(post("/events/{eventId}/signups", event.getId())
                            .with(bearer(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isCreated());

            mvc.perform(get("/events/signups")
                            .queryParam("userId", String.valueOf(member.getId()))
                            .with(bearer(board)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].user.id").value(member.getId().intValue()))
                    .andExpect(jsonPath("$[0].eventId").value(event.getId().intValue()));
        }
    }

    @Nested
    class GuestSignupByAccessToken {
        @Test
        void guestCanCreateSignupAndFetchByToken() throws Exception {
            Long committeeId = givenCommitteeId();
            EventDTO event = givenEventWithForm(committeeId);
            var board = users.get(Role.BOARD);

            var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
                es.setEventId(event.getId());
                es.setAnswers(answersFor(event, "create"));
                es.setGuest(uf.with(GuestDTO.class, g -> {
                    g.setEmail("guest.name@example.com");
                    g.setName("Guesty McGuestface");
                    g.setDiscord("Discord");
                    g.setPhoneNumber("0611111111");
                }));
                es.setUser(null);
                es.setUserId(null);
            });

            var createdRes = mvc.perform(post("/events/{eventId}/signups", event.getId())
                            .with(bearer(board))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isCreated())
                    .andReturn();

            eventSignUpDTO = mapper.readValue(createdRes.getResponse().getContentAsByteArray(), EventSignUpDTO.class);
            String token = eventSignUpDTO.getGuest().getAccessToken();
            assertNotNull(token);

            mvc.perform(get("/events/signups/byAccessToken/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        void guestCanUpdateSignupUsingAccessToken() throws Exception {
            Long committeeId = givenCommitteeId();
            EventDTO event = givenEventWithForm(committeeId);
            var board = users.get(Role.BOARD);

            var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
                es.setEventId(event.getId());
                es.setAnswers(answersFor(event, "create"));
                es.setGuest(uf.with(GuestDTO.class, g -> {
                    g.setEmail("guest.name@example.com");
                    g.setName("Guesty");
                    g.setDiscord("Discord");
                    g.setPhoneNumber("0611111111");
                }));
                es.setUser(null);
                es.setUserId(null);
            });

            var createdRes = mvc.perform(post("/events/{eventId}/signups", event.getId())
                            .with(bearer(board))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isCreated())
                    .andReturn();

            eventSignUpDTO = mapper.readValue(createdRes.getResponse().getContentAsByteArray(), EventSignUpDTO.class);
            String token = eventSignUpDTO.getGuest().getAccessToken();

            eventSignUpDTO.setAnswers(answersFor(event, "update"));
            eventSignUpDTO.getGuest().setName("Guesty Updated");

            mvc.perform(put("/events/{eventId}/signups", event.getId())
                            .queryParam("accessToken", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value(event.getId().intValue()));
        }

        @Test
        void updateByTokenFailsForMissingOrInvalidToken() throws Exception {
            Long committeeId = givenCommitteeId();
            EventDTO event = givenEventWithForm(committeeId);
            var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
                es.setEventId(event.getId());
                es.setAnswers(answersFor(event, "update"));
                es.setGuest(uf.with(GuestDTO.class, g -> {
                    g.setEmail("guest.name@example.com");
                    g.setName("Guesty McGuestface");
                    g.setDiscord("Discord");
                    g.setPhoneNumber("0611111111");
                }));
            });

            mvc.perform(put("/events/{eventId}/signups", event.getId())
                            .queryParam("accessToken", "NOT_A_REAL_TOKEN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Validation {
        @Test
        void bodyRequiresGuestOrUserWhenNoAccessToken() throws Exception {
            Long committeeId = givenCommitteeId();
            EventDTO event = givenEventWithForm(committeeId);
            var board = users.get(Role.BOARD);

            var eventSignUpDTO = uf.with(EventSignUpDTO.class, es -> {
                es.setEventId(event.getId());
                es.setAnswers(answersFor(event, "update"));
                es.setGuest(null);
                es.setUser(null);
                es.setUserId(null);
            });

            mvc.perform(put("/events/{eventId}/signups", event.getId())
                            .with(bearer(board))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(eventSignUpDTO)))
                    .andExpect(status().isBadRequest());
        }
    }
}
