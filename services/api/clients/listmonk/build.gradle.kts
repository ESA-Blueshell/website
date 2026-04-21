plugins {
    id("openapi-client-conventions")
}

openApiClient {
    specPath.set("libs/openapi-specs/listmonk.yaml")
    apiPackage.set("net.blueshell.clients.listmonk.api")
    modelPackage.set("net.blueshell.clients.listmonk.model")
    packageName.set("net.blueshell.clients.listmonk.invoker")
    apis.set(listOf("Transactional", "Bounces", "Templates", "Subscribers", "Lists"))
    schemaMappings.set(
        mapOf(
            // `per_page` is oneOf(integer, string "all") — map to Object to avoid generation issues.
            "getBounces_per_page_parameter" to "java.lang.Object",
            "getSubscribers_per_page_parameter" to "java.lang.Object",
            "getLists_per_page_parameter" to "java.lang.Object",
        ),
    )
}
