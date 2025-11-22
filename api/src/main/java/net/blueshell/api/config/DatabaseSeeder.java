package net.blueshell.api.config;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.Address;
import net.blueshell.api.model.Membership;
import net.blueshell.api.model.User;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.model.committee.CommitteeMember;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.model.event.Guest;
import net.blueshell.api.model.survey.Answer;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.model.survey.Survey;
import net.blueshell.api.service.*;
import net.blueshell.api.service.contribution.ContributionPeriodService;
import net.blueshell.api.service.contribution.ContributionService;
import net.blueshell.api.service.event.EventService;
import net.blueshell.api.service.event.EventSignUpService;
import net.blueshell.api.service.survey.SurveyService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private static final List<String> DEFAULT_RADIO_CHOICES = List.of("Option A", "Option B", "Option C", "Option D");
    private static final List<String> DEFAULT_CHECKBOX_CHOICES = List.of(
            "Choice 1", "Choice 2", "Choice 3", "Choice 4", "Choice 5"
    );

    private final UserService userService;
    private final AddressService addressService;
    private final CommitteeService committeeService;
    private final CommitteeMemberService committeeMemberService;
    private final EventService eventService;
    private final EventSignUpService eventSignUpService;
    private final MembershipService membershipService;
    private final ContributionPeriodService contributionPeriodService;
    private final ContributionService contributionService;
    private final RecoveryService recoveryService;
    private final SurveyService surveyService;
    private final PasswordEncoder passwordEncoder;
    private final Faker faker = new Faker(Locale.ENGLISH);
    private final Map<String, User> createdUsers = new LinkedHashMap<>();
    private final Random rnd = new Random(42);

    @Override
    public void run(String... args) {
        if (userService.existsByUsername("board.user")) {
            log.info("DEV database seeding skipped: already seeded.");
            return;
        }

        // ---- Contribution periods (mirrors ContributionPeriodController) ----
        var today = LocalDate.now();
        var currentPeriod = createContributionPeriod(
                today.withMonth(8).withDayOfMonth(3),
                today.withMonth(8).withDayOfMonth(2).plusYears(1)
        );
        var previousPeriod = createContributionPeriod(
                today.minusYears(1).withMonth(8).withDayOfMonth(3),
                today.withMonth(8).withDayOfMonth(2)
        );

        // ---- Committees ----
        var testCommittee = createCommittee("Test Committee", "Committee for seeded events and testing.");

        // ---- Reference users (mirrors AuthenticationController / UserController permissions expectations) ----
        var boardUser = createUserWithRole(
                "board.user",
                "Board",
                "User",
                "board.user@esa-blueshell.nl",
                Role.BOARD,
                true
        );
        var committeeUser = createUserWithRole(
                "committee.user",
                "Committee",
                "User",
                "committee.user@esa-blueshell.nl",
                Role.COMMITTEE,
                true
        );
        var memberUser = createUserWithRole(
                "member.user",
                "Member",
                "User",
                "member.user@esa-blueshell.nl",
                Role.GUEST,
                true
        );
        var normalUser = createUserWithRole(
                "normal.user",
                "Normal",
                "User",
                "normal.user@esa-blueshell.nl",
                Role.GUEST,
                true
        );
        var guestInactive = createInactiveUser(
                "guest.inactive",
                "Guest",
                "Inactive",
                "guest.inactive@esa-blueshell.nl",
                false
        );

        createdUsers.put(boardUser.getUsername(), boardUser);
        createdUsers.put(committeeUser.getUsername(), committeeUser);
        createdUsers.put(memberUser.getUsername(), memberUser);
        createdUsers.put(normalUser.getUsername(), normalUser);
        createdUsers.put(guestInactive.getUsername(), guestInactive);

        // Attach committee members (mirrors CommitteeController)
        createCommitteeMember(committeeUser, testCommittee, "Chair");
        createCommitteeMember(boardUser, testCommittee, "Board Liaison");

        // Give core reference users a real membership footprint
        createMembership(previousPeriod, boardUser);
        createMembership(currentPeriod, boardUser);
        createMembership(previousPeriod, committeeUser);
        createMembership(currentPeriod, committeeUser);
        createMembership(previousPeriod, memberUser);
        createMembership(currentPeriod, memberUser);

        createContribution(boardUser, currentPeriod);
        createContribution(committeeUser, currentPeriod);
        createContribution(memberUser, currentPeriod);

        // ---- Bulk users to make everything feel real ----
        var memberPool = new ArrayList<User>(List.of(boardUser, committeeUser, memberUser));
        var guestPool = new ArrayList<User>(List.of(normalUser));

        // Extra current members (paid/unpaid mix)
        int extraMembers = 60;
        for (int i = 0; i < extraMembers; i++) {
            var u = createRandomUser(Role.GUEST, true);
            createdUsers.put(u.getUsername(), u);

            // Give them previous+current memberships
            createMembership(previousPeriod, u);
            createMembership(currentPeriod, u);

            // Roughly 70% paid
            if (rnd.nextDouble() < 0.7) {
                createContribution(u, currentPeriod);
            }

            memberPool.add(u);
        }

        // Former members (membership ended last period)
        int formerMembers = 10;
        for (int i = 0; i < formerMembers; i++) {
            var u = createRandomUser(Role.GUEST, true);
            createdUsers.put(u.getUsername(), u);
            createMembership(previousPeriod, u);
            // No current membership
            // Keep them out of memberPool to reflect "former".
        }

        // Unpaid members (current membership but no contribution)
        int unpaidMembers = 10;
        for (int i = 0; i < unpaidMembers; i++) {
            var u = createRandomUser(Role.GUEST, true);
            createdUsers.put(u.getUsername(), u);
            createMembership(currentPeriod, u);
            memberPool.add(u);
        }

        // Guests (no membership)
        int guests = 40;
        for (int i = 0; i < guests; i++) {
            var u = createRandomUser(Role.GUEST, true);
            createdUsers.put(u.getUsername(), u);
            guestPool.add(u);
        }

        // Consume recovery tokens for enabled users (mirrors RecoveryController flows)
        createdUsers.values().forEach(u -> {
            if (u.isEnabled() && u.getRecoveryTokens() != null) {
                u.getRecoveryTokens().forEach(rt -> rt.setConsumedAt(Instant.now()));
            }
        });

        // ---- Events ----
        var eventsConfig = List.of(
                event("Past Approved Members-Only with Sign-Up", true, true, true, -30, -29, true),
                event("Past Approved Members-Only without Sign-Up", true, true, false, -25, -24, false),
                event("Past Approved Public with Sign-Up", true, false, true, -20, -19, true),
                event("Past Approved Public without Sign-Up", true, false, false, -15, -14, false),
                event("Past Not-Approved Members-Only with Sign-Up", false, true, true, -10, -9, true),
                event("Past Not-Approved Members-Only without Sign-Up", false, true, false, -8, -7, false),
                event("Past Not-Approved Public with Sign-Up", false, false, true, -6, -5, true),
                event("Past Not-Approved Public without Sign-Up", false, false, false, -4, -3, false),
                event("Future Approved Members-Only with Sign-Up", true, true, true, 5, 6, true),
                event("Future Approved Members-Only without Sign-Up", true, true, false, 7, 8, false),
                event("Future Approved Public with Sign-Up", true, false, true, 10, 11, true),
                event("Future Approved Public without Sign-Up", true, false, false, 12, 13, false),
                event("Future Not-Approved Members-Only with Sign-Up", false, true, true, 15, 16, true),
                event("Future Not-Approved Members-Only without Sign-Up", false, true, false, 17, 18, false),
                event("Future Not-Approved Public with Sign-Up", false, false, true, 20, 21, true),
                event("Future Not-Approved Public without Sign-Up", false, false, false, 22, 23, false),
                event("Event with Sign-Up Form", true, false, true, 25, 26, true)
        );

        var events = new ArrayList<Event>();
        for (var ec : eventsConfig) {
            var e = createEvent(
                    testCommittee,
                    (String) ec.get("title"),
                    (Boolean) ec.get("approved"),
                    (Boolean) ec.get("membersOnly"),
                    (Boolean) ec.get("signUp"),
                    (Integer) ec.get("startOffset"),
                    (Integer) ec.get("endOffset"),
                    (Boolean) ec.get("withForm")
            );
            events.add(e);
        }

        // Create a "previously approved" event, then revoke it after collecting sign-ups (mirrors approve endpoint)
        var previouslyApproved = createEvent(
                testCommittee,
                "Previously Approved Event",
                true,     // start as approved
                false,
                true,
                30,
                31,
                true
        );
        events.add(previouslyApproved);

        // Seed initial "reference" signups like controllers would set principal → userId
        for (var e : events) {
            if (e.isSignUp()) {
                createEventSignUpWithAnswers(memberUser, e);
                if (!e.isMembersOnly()) {
                    createEventSignUpWithAnswers(normalUser, e);
                }
            }
        }

        // Add lots of realistic signups, avoiding duplicates and respecting members-only
        for (var e : events) {
            if (e.isSignUp()) seedSignUps(e, memberPool, guestPool);
        }

        // Add guest sign-ups for public events with sign-up enabled
        for (var e : events) {
            if (e.isSignUp() && !e.isMembersOnly()) {
                seedGuestSignUps(e);
            }
        }

        // Add a couple of explicit sign-ups to the "previously approved" event
        createEventSignUpWithAnswers(boardUser, previouslyApproved);
        createEventSignUpWithAnswers(committeeUser, previouslyApproved);

        // Revoke approval on that event (mirrors EventController.approve)
        previouslyApproved = eventService.findById(previouslyApproved.getId());
        previouslyApproved.setApproved(false);
        eventService.update(previouslyApproved);

        log.info("Database seeding completed.");
        log.info("Created users ({}): {}", createdUsers.size(), String.join(", ", createdUsers.keySet()));
        log.info("Created {} events", events.size());
    }

    // ---- Helpers below ------------------------------------------------------

    private User createRandomUser(Role baseRole, boolean includeAddress) {
        String first = safeName(faker.name().firstName());
        String last = safeName(faker.name().lastName());
        String base = (first + "." + last).toLowerCase();
        String username = uniqueUsername(base);
        String email = username + "@esa-blueshell.nl";

        var u = createUserWithRole(username, first, last, email, baseRole, includeAddress);

        // Mirror AddressController semantics if we want to "add address after create"
        if (!includeAddress && rnd.nextBoolean()) {
            var addr = createAddressEntity();
            u.setAddress(addr);
            u = userService.update(u);
        }

        return u;
    }

    private String safeName(String s) {
        return s.replaceAll("[^A-Za-z0-9]", "");
    }

    private String uniqueUsername(String base) {
        String u = base;
        int i = 1;
        while (userService.existsByUsername(u)) {
            u = base + "." + i++;
        }
        return u;
    }

    private Address buildAddress() {
        var address = new Address();
        address.setStreet(faker.address().streetName());
        address.setHouseNumber(String.valueOf(faker.number().numberBetween(1, 200)));
        address.setCity(faker.address().city());
        address.setZipCode(faker.address().zipCode());
        address.setCountry(faker.address().country());
        return address;
    }

    private Membership createMembership(ContributionPeriod period, User user) {
        var membership = new Membership();
        membership.setStartDate(period.getStartDate());
        membership.setEndDate(period.getEndDate());
        membership.setMemberType(MemberType.REGULAR);
        membership.setUserId(user.getId());
        return membershipService.create(membership);
    }

    private Map<String, Object> event(
            String title,
            boolean approved,
            boolean membersOnly,
            boolean signUp,
            int startOffset,
            int endOffset,
            boolean withForm
    ) {
        var m = new LinkedHashMap<String, Object>();
        m.put("title", title);
        m.put("approved", approved);
        m.put("membersOnly", membersOnly);
        m.put("signUp", signUp);
        m.put("startOffset", startOffset);
        m.put("endOffset", endOffset);
        m.put("withForm", withForm);
        return m;
    }

    private User createUserWithRole(
            String username,
            String firstName,
            String lastName,
            String email,
            Role role,
            boolean includeAddress
    ) {
        var user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setEnabled(true);
        user.setRoles(role.getAllInheritedRoles());
        user.setNewsletter(false);
        user.setDateOfBirth(Date.valueOf(LocalDate.of(1995, 1, 1)));
        user.setConsentPrivacy(true);
        user.setConsentGdpr(true);
        if (includeAddress) user.setAddress(createAddressEntity());
        return userService.create(user);
    }

    private User createInactiveUser(
            String username,
            String firstName,
            String lastName,
            String email,
            boolean includeAddress
    ) {
        var user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("temporary"));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setEnabled(false);
        user.setRoles(Set.of(Role.GUEST));
        user.setNewsletter(false);
        user.setDateOfBirth(Date.valueOf(LocalDate.of(1995, 1, 1)));
        user.setConsentPrivacy(true);
        user.setConsentGdpr(true);
        if (includeAddress) user.setAddress(createAddressEntity());
        return userService.create(user);
    }

    private Address createAddressEntity() {
        var address = new Address();
        address.setCountry("Netherlands");
        address.setCity("Amsterdam");
        address.setStreet("Test Street");
        address.setHouseNumber("123");
        address.setZipCode("1234 AB");
        return address;
    }

    private Committee createCommittee(String name, String description) {
        var committee = new Committee();
        committee.setName(name);
        committee.setDescription(description);
        committee.setMembers(new HashSet<>());
        return committeeService.create(committee);
    }

    private CommitteeMember createCommitteeMember(User user, Committee committee, String role) {
        var member = new CommitteeMember();
        member.setUserId(user.getId());
        member.setCommittee(committee);
        member.setRole(role);
        return committeeMemberService.create(member);
    }

    private ContributionPeriod createContributionPeriod(LocalDate start, LocalDate end) {
        var period = new ContributionPeriod();
        period.setStartDate(start);
        period.setEndDate(end);
        period.setHalfYearFee(10.0);
        period.setFullYearFee(18.0);
        period.setAlumniFee(5.0);
        return contributionPeriodService.create(period);
    }

    private Contribution createContribution(User user, ContributionPeriod period) {
        var c = new Contribution();
        c.setUserId(user.getId());
        c.setContributionPeriodId(period.getId());
        return contributionService.create(c);
    }

    private Event createEvent(
            Committee committee,
            String title,
            boolean approved,
            boolean membersOnly,
            boolean signUp,
            int startDaysOffset,
            int endDaysOffset,
            boolean withForm
    ) {
        Survey survey = null;
        if (signUp && withForm) {
            survey = createSurveyWithAllQuestionTypes();
            // If cascade PERSIST is not configured on Event.signUpForm, uncomment:
            // survey = surveyService.create(survey);
        }
        var event = new Event();
        event.setCommitteeId(committee.getId());
        event.setTitle(title);
        event.setDescription("Description for " + title);
        event.setLocation("Test Location");
        event.setStartTime(Instant.now().plus(startDaysOffset, ChronoUnit.DAYS));
        event.setEndTime(Instant.now().plus(endDaysOffset, ChronoUnit.DAYS));
        event.setApproved(approved);
        event.setMembersOnly(membersOnly);
        event.setSignUp(signUp);
        event.setMemberPrice(10.0);
        event.setPublicPrice(15.0);
        event.setSignUpForm(survey);
        return eventService.create(event);
    }

    private Optional<EventSignUp> findExistingSignUp(Long eventId, Long userId) {
        return eventSignUpService.findByEventId(eventId).stream()
                .filter(su -> Objects.equals(su.getUserId(), userId))
                .findFirst();
    }

    private EventSignUp createEventSignUpWithAnswers(User user, Event event) {
        // 1) Idempotency guard: user can sign up to an event only once
        var existing = findExistingSignUp(event.getId(), user.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        var signUp = new EventSignUp();
        signUp.setEventId(event.getId());
        signUp.setUserId(user.getId());

        var answers = new LinkedHashSet<Answer>();
        var form = event.getSignUpForm();
        if (event.isSignUp() && form != null && form.getQuestions() != null) {
            for (var q : form.getQuestions()) {
                answers.add(createAnswerForQuestion(q));
            }
        }
        signUp.setAnswers(answers);

        try {
            // 2) Normal path
            return eventSignUpService.create(signUp);
        } catch (DataIntegrityViolationException ex) {
            // 3) Defensive: if another thread/seed pass created it just now, fetch and return it
            log.warn(
                    "Sign-up already exists for eventId={}, userId={}, skipping duplicate insert.",
                    event.getId(), user.getId()
            );
            return findExistingSignUp(event.getId(), user.getId())
                    .orElseThrow(() -> ex); // rethrow only if truly not present
        }
    }

    private void seedSignUps(Event event, List<User> memberPool, List<User> guestPool) {
        // Build candidate list
        List<User> candidates = new ArrayList<>(memberPool);
        if (!event.isMembersOnly()) candidates.addAll(guestPool);

        // Avoid duplicates: find existing signups for this event
        Set<Long> existingUserIds = eventSignUpService.findByEventId(event.getId()).stream()
                .map(EventSignUp::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Shuffle and pick a realistic count
        Collections.shuffle(candidates, rnd);
        int maxDesired = Math.min(candidates.size(), rnd.nextInt(20, 45));
        int added = 0;

        for (User u : candidates) {
            if (added >= maxDesired) break;
            if (existingUserIds.contains(u.getId())) continue;
            createEventSignUpWithAnswers(u, event);
            existingUserIds.add(u.getId());
            added++;
        }
    }

    private Survey createSurveyWithAllQuestionTypes() {
        var survey = new Survey();
        var questions = new LinkedHashSet<Question>();
        questions.add(buildQuestion(1L, QuestionType.DESCRIPTION, "Important information:", null));
        questions.add(buildQuestion(
                2L,
                QuestionType.RADIO,
                "Please select one option:",
                new ArrayList<>(DEFAULT_RADIO_CHOICES)
        ));
        questions.add(buildQuestion(
                3L,
                QuestionType.CHECKBOX,
                "Select all that apply:",
                new ArrayList<>(DEFAULT_CHECKBOX_CHOICES)
        ));
        questions.add(buildQuestion(4L, QuestionType.OPEN, "What are your thoughts?", null));
        survey.setQuestions(questions);
        for (var q : survey.getQuestions()) q.setSurvey(survey);
        return survey;
    }

    private Question buildQuestion(Long idx, QuestionType type, String label, List<String> choiceLabels) {
        var q = new Question();
        q.setIdx(idx);
        q.setType(type);
        q.setLabel(label);
        q.setChoiceLabels(choiceLabels);
        return q;
    }

    private Answer createAnswerForQuestion(Question q) {
        var a = new Answer();
        a.setQuestion(q);
        a.setQuestionId(q.getId());
        switch (q.getType()) {
            case OPEN -> a.setTextResponse("Sample answer text");
            case RADIO -> {
                var n = sizeOfChoices(q);
                var selections = new ArrayList<>(Collections.nCopies(Math.max(n, 1), Boolean.FALSE));
                if (n > 0) selections.set(0, Boolean.TRUE);
                a.setOptionSelections(selections);
            }
            case CHECKBOX -> {
                var n = sizeOfChoices(q);
                var selections = new ArrayList<>(Collections.nCopies(Math.max(n, 1), Boolean.FALSE));
                if (n > 0) selections.set(0, Boolean.TRUE);
                if (n > 2) selections.set(2, Boolean.TRUE);
                a.setOptionSelections(selections);
            }
            case DESCRIPTION -> {
                // no-op
            }
        }
        return a;
    }

    private int sizeOfChoices(Question q) {
        return q.getChoiceLabels() == null ? 0 : q.getChoiceLabels().size();
    }

    private Guest buildRandomGuest() {
        var guest = new Guest();
        guest.setName(faker.name().fullName());
        guest.setDiscord(faker.name().username() + "#" + faker.number().numberBetween(1000, 9999));
        guest.setEmail(faker.internet().emailAddress());
        guest.setPhoneNumber(faker.phoneNumber().phoneNumber());
        guest.setAccessToken(UUID.randomUUID().toString());
        return guest;
    }

    private EventSignUp createGuestEventSignUpWithAnswers(Guest guest, Event event) {
        var signUp = new EventSignUp();
        signUp.setEventId(event.getId());
        signUp.setUserId(null); // guest signup, not a registered user
        signUp.setGuest(guest);

        var answers = new LinkedHashSet<Answer>();
        var form = event.getSignUpForm();
        if (event.isSignUp() && form != null && form.getQuestions() != null) {
            for (var q : form.getQuestions()) {
                answers.add(createAnswerForQuestion(q));
            }
        }
        signUp.setAnswers(answers);

        return eventSignUpService.create(signUp);
    }

    private void seedGuestSignUps(Event event) {
        // Only public events with sign-up should get guest sign-ups
        if (event.isMembersOnly() || !event.isSignUp()) {
            return;
        }

        // Between 5 and 20 guest signups per public sign-up event
        int guestCount = rnd.nextInt(5, 21);

        for (int i = 0; i < guestCount; i++) {
            var guest = buildRandomGuest();
            createGuestEventSignUpWithAnswers(guest, event);
        }
    }
}
