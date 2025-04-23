package net.blueshell.api.service.brevo;

import net.blueshell.api.mapping.BrevoContactMapper;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sendinblue.ApiClient;
import sendinblue.ApiException;
import sendinblue.Configuration;
import sendinblue.auth.ApiKeyAuth;
import sibApi.ContactsApi;
import sibModel.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ContactService {

    @Autowired
    private BrevoContactMapper brevoContactMapper;

    @Value("${brevo.apiKey}")
    private String apiKey;
    @Value("${brevo.folders.contributionPeriodsId}")
    private Long contributionPeriodsFolder;

    private ContactsApi getContactsApi() {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
        apiKeyAuth.setApiKey(this.apiKey);
        return new ContactsApi();
    }

    // Retrieves a User from Brevo without overwriting non-null values.
    public User getUserFromBrevo(String email) throws ApiException {
        ContactsApi api = getContactsApi();
        GetExtendedContactDetails details = api.getContactInfo(email, null, null);
        // Create a new User instance (or use an existing one) and map attributes.
        User user = new User();
        Map<String, Object> attributes = (Map<String, Object>) details.getAttributes();
        brevoContactMapper.attributesToUser(attributes, user);
        return user;
    }

    public User getUpdate(User user) throws ApiException {
        if (user.getContactId() != null) {
            return user;
        }
        try {
            ContactsApi api = getContactsApi();
            GetExtendedContactDetails details = api.getContactInfo(user.getEmail(), null, null);
            user.setContactId(details.getId());
        } catch (ApiException e) {
            e.printStackTrace();
        }
        return user;
    }

    public void updateContact(User user) throws ApiException {
        user = getUpdate(user);
        if (user.getContactId() != null) {
            sendUpdate(user);
        } else {
            createContact(user);
        }
    }

    private void createContact(User user) throws ApiException {
        ContactsApi api = getContactsApi();
        CreateContact createContact = new CreateContact();
        createContact.setEmail(user.getEmail());
        // Use the mapper to create the attributes map (null values are filtered out).
        Map<String, Object> attributes = brevoContactMapper.userToAttributes(user);
        createContact.setAttributes(attributes);
        CreateUpdateContactModel response = api.createContact(createContact);
        user.setContactId(response.getId());
    }

    private void sendUpdate(User user) throws ApiException {
        ContactsApi api = getContactsApi();
        UpdateContact updateContact = new UpdateContact();
        // Again, use the mapper so that only non-null attributes are updated.
        updateContact.setAttributes(brevoContactMapper.userToAttributes(user));
        api.updateContact(user.getContactId().toString(), updateContact);
    }

    public void createContributionPeriodList(ContributionPeriod contributionPeriod) throws ApiException {
        ContactsApi api = getContactsApi();
        CreateList createList = new CreateList();
        String periodName = String.format("Contribution Paid %d - %d",
                contributionPeriod.getStartDate().getYear(), contributionPeriod.getEndDate().getYear());
        createList.name(periodName);
        createList.setFolderId(contributionPeriodsFolder);
        CreateModel createModel = api.createList(createList);
        contributionPeriod.setListId(createModel.getId());
    }

    public void addToContributionPeriodList(ContributionPeriod contributionPeriod, User user) throws ApiException {
        ContactsApi api = getContactsApi();
        List<Long> ids = new ArrayList<>();
        ids.add(user.getContactId());
        AddContactToList contactEmails = new AddContactToList();
        contactEmails.setIds(ids);
        api.addContactToList(contributionPeriod.getListId(), contactEmails);
    }

    public void removeFromContributionPeriodList(ContributionPeriod contributionPeriod, User user) throws ApiException {
        ContactsApi api = getContactsApi();
        List<Long> ids = new ArrayList<>();
        ids.add(user.getContactId());
        RemoveContactFromList contactEmails = new RemoveContactFromList();
        contactEmails.setIds(ids);
        api.removeContactFromList(contributionPeriod.getListId(), contactEmails);
    }
}
