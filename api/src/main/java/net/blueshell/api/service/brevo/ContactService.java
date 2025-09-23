package net.blueshell.api.service.brevo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.mapper.BrevoContactMapper;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.model.User;
import net.blueshell.api.service.UserService;
import net.blueshell.clients.brevo.api.ContactsApi;
import net.blueshell.clients.brevo.invoker.ApiClient;
import net.blueshell.clients.brevo.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Slf4j
@Service
public class ContactService {

    private final BrevoContactMapper mapper;
    private final UserService users;

    @Value("${brevo.apiKey}")
    private String apiKey;
    @Value("${brevo.folders.contributionPeriodsId}")
    private Long contributionPeriodsFolder;

    private ContactsApi contacts;

    public ContactService(BrevoContactMapper mapper, UserService users) {
        this.mapper = mapper;
        this.users = users;
    }

    private ContactsApi getContactsApi() {
        DateFormat dateFormat = ApiClient.createDefaultDateFormat();
        ObjectMapper objectMapper = ApiClient.createDefaultObjectMapper(dateFormat)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .setDefaultPropertyInclusion(
                        JsonInclude.Value.construct(
                                JsonInclude.Include.NON_EMPTY,
                                JsonInclude.Include.NON_EMPTY
                        )
                );

        ApiClient apiClient = new ApiClient(objectMapper, dateFormat);
        apiClient.setApiKey(this.apiKey);
        return new ContactsApi(apiClient);
    }

    public void getUpdate(User user) {
        if (user.getContactId() != null) return;
        log.info("Getting update for user: {}", user.getEmail());

        try {
            ContactsApi api = getContactsApi();
            GetExtendedContactDetails details =
                    api.getContactInfo(user.getEmail(), "email_id", null, null);
            user.setContactId(details.getId());
        } catch (HttpClientErrorException e) {
            log.info("Failed to get contact details for user: {}", user.getEmail());
        }
    }

    public void sync(User user) {
        getUpdate(user);
        if (user.getContactId() != null) {
            sendUpdate(user);
        } else {
            createContact(user);
        }
    }

    private void createContact(User user) throws RestClientResponseException {
        log.info("Creating contact for user: {}", user.getEmail());
        ContactsApi api = getContactsApi();
        CreateContact createContact = mapper.toCreate(user);
        CreateUpdateContactModel response = api.createContact(createContact);
        user.setContactId(response.getId());
    }

    private void sendUpdate(User user) throws RestClientResponseException {
        log.info("Sending update for user: {}", user.getEmail());
        var api = getContactsApi();
        var contact = mapper.toUpdate(user);
        api.updateContact(
                user.getEmail(),
                contact,
                "email_id"
        );
    }

    public Long createList(ContributionPeriod contributionPeriod) throws RestClientResponseException {
        if (contributionPeriod.getListId() != null) {
            return contributionPeriod.getListId();
        }

        ContactsApi api = getContactsApi();
        CreateList createList = new CreateList();
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();
        startCalendar.setTime(contributionPeriod.getStartDate());
        endCalendar.setTime(contributionPeriod.getEndDate());
        String periodName = String.format("Contribution Paid %d - %d",
                startCalendar.get(Calendar.YEAR), endCalendar.get(Calendar.YEAR));
        createList.name(periodName);
        createList.setFolderId(contributionPeriodsFolder);
        CreateModel createModel = api.createList(createList);
        return createModel.getId();
    }

    public void addToList(ContributionPeriod contributionPeriod, User user) throws RestClientResponseException {
        if (user.getContactId() == null) {
            sync(user);
            users.update(user);
        }

        ContactsApi api = getContactsApi();
        List<Long> ids = new ArrayList<>();
        ids.add(user.getContactId());
        AddContactToListRequest payload = new AddContactToListRequest();
        payload.setIds(ids);
        api.addContactToList(contributionPeriod.getListId(), payload);
    }

    public void removeFromList(ContributionPeriod contributionPeriod, User user) throws RestClientResponseException {
        ContactsApi api = getContactsApi();
        List<Long> ids = new ArrayList<>();
        ids.add(user.getContactId());
        RemoveContactFromListRequest payload = new RemoveContactFromListRequest();
        payload.setIds(ids);
        api.removeContactFromList(contributionPeriod.getListId(), payload);
    }
}
