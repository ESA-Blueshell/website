package net.blueshell.api.service.brevo;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.mapper.BrevoContactMapper;
import net.blueshell.api.model.User;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MockContactService: drop-in replacement for ContactService that never calls Brevo.
 * <p>
 * Activate with either `@ActiveProfiles("brevo-mock")` in tests or
 * `spring.profiles.active=brevo-mock` for local runs.
 * <p>
 * This class keeps all state in-memory:
 * - contacts are keyed by email -> generated contactId
 * - lists are keyed by listId -> set of contactIds
 */
@Slf4j
@Service
@Profile({"test", "brevo-mock"})
@Primary
public class MockContactService extends ContactService {

    private final AtomicLong contactSeq = new AtomicLong(100_000L);
    private final AtomicLong listSeq = new AtomicLong(10_000L);

    private final ConcurrentMap<String, Long> emailToContactId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Set<Long>> listMembers = new ConcurrentHashMap<>();

    @Autowired
    private UserService users;

    public MockContactService(BrevoContactMapper mapper, UserService users) {
        super(mapper, users);
    }

    /**
     * Does NOT call Brevo. If we already "know" this email, populate the user's contactId.
     */
    @Override
    public void getUpdate(User user) {
        if (user.getContactId() != null) return;
        Long id = emailToContactId.get(user.getEmail());
        if (id != null) {
            user.setContactId(id);
            log.debug("[brevo-mock] found existing contactId={} for email={}", id, user.getEmail());
        } else {
            log.debug("[brevo-mock] no contact yet for email={}", user.getEmail());
        }
    }

    /**
     * Does NOT call Brevo. If the user has no contactId, we generate one; otherwise act as a no-op update.
     */
    @Override
    public void sync(User user) {
        getUpdate(user);
        if (user.getContactId() == null) {
            long id = emailToContactId.computeIfAbsent(user.getEmail(), k -> contactSeq.getAndIncrement());
            user.setContactId(id);
            log.info("[brevo-mock] created contact email={} -> id={}", user.getEmail(), id);
        } else {
            log.info("[brevo-mock] updated contact email={} id={}", user.getEmail(), user.getContactId());
        }
    }

    /**
     * Does NOT call Brevo. Returns existing listId or creates a new one and tracks it in-memory.
     */
    @Override
    public Long createList(ContributionPeriod contributionPeriod) throws RestClientResponseException {
        Long listId = contributionPeriod.getListId();
        if (listId == null) {
            listId = listSeq.getAndIncrement();
            log.info("[brevo-mock] created listId={} for contributionPeriod id={}", listId, contributionPeriod.getId());
        }
        listMembers.putIfAbsent(listId, ConcurrentHashMap.newKeySet());
        return listId;
    }

    /**
     * Does NOT call Brevo. Ensures the user has a contactId, then adds it to the in-memory list membership.
     */
    @Override
    public void addToList(ContributionPeriod contributionPeriod, User user) throws RestClientResponseException {
        if (user.getContactId() == null) {
            sync(user);
            users.update(user);
        }
        Long listId = contributionPeriod.getListId();
        if (listId == null) {
            listId = createList(contributionPeriod);
        }
        listMembers.computeIfAbsent(listId, k -> ConcurrentHashMap.newKeySet()).add(user.getContactId());
        log.info("[brevo-mock] added contactId={} to listId={}", user.getContactId(), listId);
    }

    /**
     * Does NOT call Brevo. Removes the user's contactId from the in-memory list membership if present.
     */
    @Override
    public void removeFromList(ContributionPeriod contributionPeriod, User user) throws RestClientResponseException {
        Long listId = contributionPeriod.getListId();
        if (listId == null) return;
        listMembers.computeIfPresent(listId, (id, set) -> {
            set.remove(user.getContactId());
            return set;
        });
        log.info("[brevo-mock] removed contactId={} from listId={}", user.getContactId(), listId);
    }

    /**
     * Expose read-only views for assertions in tests.
     */
    public Map<String, Long> getEmailToContactId() {
        return Collections.unmodifiableMap(emailToContactId);
    }

    public Map<Long, Set<Long>> getListMembers() {
        return Collections.unmodifiableMap(listMembers);
    }
}
