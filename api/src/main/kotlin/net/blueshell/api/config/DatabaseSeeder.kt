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

    // Track committee sizes & memberships to enforce max 6 members
    private final Map<Long, Integer> committeeMemberCounts = new HashMap<>();
    private final Set<String> committeeMemberships = new HashSet<>();

    // Track contributions per period to enforce contribution ratios
    // key: contributionPeriodId -> set of userIds who have a contribution in that period
    private final Map<Long, Set<Long>> contributionsByPeriod = new HashMap<>();

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

        // Track which users are members per period so we can enforce ratios later
        var previousPeriodMembers = new ArrayList<User>();
        var currentPeriodMembers = new ArrayList<User>();

        // ---- Committees ----
        var testCommittee = createCommittee("Test Committee", "Committee for seeded events and testing.");
        var activitiesCommittee = createCommittee("Activities Committee", "Organises general activities for members.");
        var sportsCommittee = createCommittee("Sports Committee", "Handles sports-related events and tournaments.");
        var itCommittee = createCommittee("IT Committee", "Maintains systems, website and IT tooling.");
        var socialCommittee = createCommittee("Social Committee", "Organises social gatherings and borrels.");

        var allCommittees = List.of(testCommittee, activitiesCommittee, sportsCommittee, itCommittee, socialCommittee);

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
        previousPeriodMembers.add(boardUser);
        createMembership(currentPeriod, boardUser);
        currentPeriodMembers.add(boardUser);

        createMembership(previousPeriod, committeeUser);
        previousPeriodMembers.add(committeeUser);
        createMembership(currentPeriod, committeeUser);
        currentPeriodMembers.add(committeeUser);

        createMembership(previousPeriod, memberUser);
        previousPeriodMembers.add(memberUser);
        createMembership(currentPeriod, memberUser);
        currentPeriodMembers.add(memberUser);

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
            previousPeriodMembers.add(u);
            createMembership(currentPeriod, u);
            currentPeriodMembers.add(u);

            // Roughly 70% paid (we'll fix aggregate ratios later)
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
            previousPeriodMembers.add(u);
            // No current membership
            // Keep them out of memberPool to reflect "former".
        }

        // Unpaid members (current membership but no contribution)
        int unpaidMembers = 10;
        for (int i = 0; i < unpaidMembers; i++) {
            var u = createRandomUser(Role.GUEST, true);
            createdUsers.put(u.getUsername(), u);
            createMembership(currentPeriod, u);
            currentPeriodMembers.add(u);
            memberPool.add(u);
        }

        // Guests (no membership)
        int guests = 40;
        for (int i = 0; i < guests; i++) {
            var u = createRandomUser(Role.GUEST, true);
            createdUsers.put(u.getUsername(), u);
            guestPool.add(u);
        }

        // Ensure at least 75% of past members and 60% of current members paid their contribution
        ensureContributionRatios(previousPeriod, currentPeriod, previousPeriodMembers, currentPeriodMembers);

        // Seed additional committee members from randomly generated members, max 6 per committee
        seedCommitteeMembers(allCommittees, memberPool);

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
        int committeeIndex = 0;
        for (var ec : eventsConfig) {
            // Distribute events across committees so they all host events
            Committee hostingCommittee;
            if (committeeIndex < allCommittees.size()) {
                hostingCommittee = allCommittees.get(committeeIndex);
            } else {
                hostingCommittee = allCommittees.get(rnd.nextInt(allCommittees.size()));
            }
            committeeIndex++;

            var e = createEvent(
                    hostingCommittee,
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

        // Make some random users inactive (not one of the original few reference users)
        deactivateRandomUsers(
                10,
                createdUsers.values(),
                Set.of(
                        boardUser.getUsername(),
                        committeeUser.getUsername(),
                        memberUser.getUsername(),
                        normalUser.getUsername(),
                        guestInactive.getUsername()
                )
        );

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
        if (committee == null || user == null) return null;

        String key = committee.getId() + ":" + user.getId();
        if (committeeMemberships.contains(key)) {
            // Already in this committee; avoid duplicates
            return null;
        }

        int currentCount = committeeMemberCounts.getOrDefault(committee.getId(), 0);
        if (currentCount >= 6) {
            log.debug(
                    "Committee '{}' already has max members (6), skipping {}",
                    committee.getName(),
                    user.getUsername()
            );
            return null;
        }

        var member = new CommitteeMember();
        member.setUserId(user.getId());
        member.setCommittee(committee);
        member.setRole(role);

        CommitteeMember created = committeeMemberService.create(member);
        committeeMemberCounts.put(committee.getId(), currentCount + 1);
        committeeMemberships.add(key);
        return created;
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
        var created = contributionService.create(c);

        contributionsByPeriod
                .computeIfAbsent(period.getId(), id -> new HashSet<>())
                .add(user.getId());

        return created;
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
        event.setCommitteeId(committee != null ? committee.getId() : null);
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

    // ---- New helper logic for contribution ratios, committees & inactive users ----
    private void ensureContributionRatios(
            ContributionPeriod previousPeriod,
            ContributionPeriod currentPeriod,
            List<User> previousMembers,
            List<User> currentMembers
    ) {
        enforceContributionRatioForPeriod(previousPeriod, previousMembers, 0.75); // 75% past members
        enforceContributionRatioForPeriod(currentPeriod, currentMembers, 0.60);  // 60% current members
    }

    private void enforceContributionRatioForPeriod(
            ContributionPeriod period,
            List<User> members,
            double ratio
    ) {
        if (members.isEmpty()) return;

        // Deduplicate by user id
        List<User> distinctMembers = members.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(User::getId, u -> u, (u1, u2) -> u1),
                        m -> new ArrayList<>(m.values())
                ));

        int total = distinctMembers.size();
        if (total == 0) return;

        int desiredPaid = (int) Math.round(total * ratio);
        Set<Long> paidUserIds = contributionsByPeriod
                .getOrDefault(period.getId(), Collections.emptySet());

        int currentPaid = paidUserIds.size();
        if (currentPaid >= desiredPaid) {
            // Already at or above the desired ratio
            return;
        }

        List<User> unpaid = distinctMembers.stream()
                .filter(u -> !paidUserIds.contains(u.getId()))
                .collect(Collectors.toList());

        Collections.shuffle(unpaid, rnd);
        int needed = Math.min(desiredPaid - currentPaid, unpaid.size());
        for (int i = 0; i < needed; i++) {
            createContribution(unpaid.get(i), period);
        }
    }

    private void seedCommitteeMembers(List<Committee> committees, List<User> memberPool) {
        if (committees == null || committees.isEmpty()) return;

        // Use randomly generated members, but avoid spamming the original reference users
        Set<String> coreUsernames = Set.of(
                "board.user",
                "committee.user",
                "member.user",
                "normal.user",
                "guest.inactive"
        );

        List<User> candidates = memberPool.stream()
                .filter(u -> u != null && !coreUsernames.contains(u.getUsername()))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(User::getId, u -> u, (u1, u2) -> u1),
                        m -> new ArrayList<>(m.values())
                ));

        Collections.shuffle(candidates, rnd);
        Iterator<User> iterator = candidates.iterator();

        for (Committee committee : committees) {
            int currentCount = committeeMemberCounts.getOrDefault(committee.getId(), 0);
            int remainingSlots = 6 - currentCount;
            if (remainingSlots <= 0) continue;

            // Aim for between 3 and 6 members total per committee
            int desiredTotal = rnd.nextInt(3, 7);
            int targetAdditionalMembers = Math.max(0, desiredTotal - currentCount);
            targetAdditionalMembers = Math.min(targetAdditionalMembers, remainingSlots);

            for (int i = 0; i < targetAdditionalMembers && iterator.hasNext(); i++) {
                User candidate = iterator.next();
                String role = pickRoleForPosition(currentCount + i);
                createCommitteeMember(candidate, committee, role);
            }
        }
    }

    private String pickRoleForPosition(int position) {
        // Some sensible role distribution: Chair, Secretary, Treasurer, Event Manager, PR, Member
        return switch (position) {
            case 0 -> "Chair";
            case 1 -> "Secretary";
            case 2 -> "Treasurer";
            case 3 -> "Event Manager";
            case 4 -> "PR Officer";
            default -> "Member";
        };
    }

    private void deactivateRandomUsers(int count, Collection<User> allUsers, Set<String> excludedUsernames) {
        List<User> candidates = allUsers.stream()
                .filter(Objects::nonNull)
                .filter(User::isEnabled)
                .filter(u -> !excludedUsernames.contains(u.getUsername()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return;

        Collections.shuffle(candidates, rnd);
        int limit = Math.min(count, candidates.size());

        for (int i = 0; i < limit; i++) {
            var u = userService.findById(candidates.get(i).getId());

            u.setEnabled(false);
            var updated = userService.update(u);
            createdUsers.put(updated.getUsername(), updated);
        }
    }
}
