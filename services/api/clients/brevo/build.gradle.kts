plugins {
    id("openapi-client-conventions")
}

openApiClient {
    specPath.set("libs/openapi-specs/brevo.yml")
    apiPackage.set("net.blueshell.clients.brevo.api")
    modelPackage.set("net.blueshell.clients.brevo.model")
    packageName.set("net.blueshell.clients.brevo.invoker")
    apis.set(listOf("TransactionalEmails", "Contacts"))
    schemaMappings.set(
        mapOf(
            "getContactInfo_identifier_parameter" to "java.lang.String",
            "updateContact_identifier_parameter" to "java.lang.String",
            "createDoiContact_attributes_value" to "java.lang.Object",
            "getContactInfo_identifierType_parameter" to "java.lang.String",
            "updateContact_identifierType_parameter" to "java.lang.String",
            "TemplatePreviewRequestBody" to "net.blueshell.clients.brevo.model.TemplatePreviewRequestBody",
            "postContactInfo_contacts_success" to "java.lang.Object",
            "postContactInfo_contacts_failure" to "java.lang.Object",
        ),
    )
}
