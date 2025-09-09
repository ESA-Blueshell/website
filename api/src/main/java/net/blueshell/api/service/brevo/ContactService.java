package net.blueshell.api.service.brevo;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.mapper.BrevoContactMapper;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import net.blueshell.clients.brevo.api.ContactsApi;
import net.blueshell.clients.brevo.invoker.ApiClient;
import net.blueshell.clients.brevo.invoker.auth.ApiKeyAuth;
import net.blueshell.clients.brevo.model.AddContactToListRequest;
import net.blueshell.clients.brevo.model.CreateContact;
import net.blueshell.clients.brevo.model.CreateList;
import net.blueshell.clients.brevo.model.CreateModel;
import net.blueshell.clients.brevo.model.CreateUpdateContactModel;
import net.blueshell.clients.brevo.model.GetExtendedContactDetails;
import net.blueshell.clients.brevo.model.RemoveContactFromListRequest;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ContactService {

    private final BrevoContactMapper mapper;

    @Value("${brevo.apiKey}")
    private String apiKey;
    @Value("${brevo.folders.contributionPeriodsId}")
    private Long contributionPeriodsFolder;

    private ContactsApi contacts;

    public ContactService(BrevoContactMapper mapper) {
        this.mapper = mapper;
    }

    private ContactsApi getContactsApi() {
        ApiClient apiClient = new ApiClient();
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) apiClient.getAuthentication("api-key");
        apiKeyAuth.setApiKey(this.apiKey);
        return new ContactsApi(apiClient);
    }

    public User getUpdate(User user) {
        if (user.getContactId() != null) {
            return user;
        }
        log.info("Contact ID not found for user {}. Creating new contact.", user.getEmail());
        ContactsApi api = getContactsApi();
        GetExtendedContactDetails details =
                api.getContactInfo(user.getEmail(), "email_id", null, null);
        user.setContactId(details.getId());

        return user;
    }

    public void sync(User user) {
        user = getUpdate(user);
        if (user.getContactId() != null) {
            sendUpdate(user);
        } else {
            createContact(user);
        }
    }

    private void createContact(User user) throws RestClientResponseException {
        ContactsApi api = getContactsApi();
        CreateContact createContact = mapper.toCreate(user);
        CreateUpdateContactModel response = api.createContact(createContact);
        user.setContactId(response.getId());
    }

    private void sendUpdate(User user) throws RestClientResponseException {
        var api = getContactsApi();
        var contact = mapper.toUpdate(user);
        // Use contact_id as identifierType when updating by numeric ID
        api.updateContact(
                user.getEmail(),
                contact,
                "email_id"
        );
    }

    public Long createList(ContributionPeriod contributionPeriod) throws RestClientResponseException {
        ContactsApi api = getContactsApi();
        CreateList createList = new CreateList();
        String periodName = String.format("Contribution Paid %d - %d",
                contributionPeriod.getStartDate().getYear(), contributionPeriod.getEndDate().getYear());
        createList.name(periodName);
        createList.setFolderId(contributionPeriodsFolder);
        CreateModel createModel = api.createList(createList);
        return createModel.getId();
    }

    public void addToList(ContributionPeriod contributionPeriod, User user) throws RestClientResponseException {
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
