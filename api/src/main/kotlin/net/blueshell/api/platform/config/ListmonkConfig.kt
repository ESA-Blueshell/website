package net.blueshell.api.platform.config

import net.blueshell.clients.listmonk.api.BouncesApi
import net.blueshell.clients.listmonk.api.TransactionalApi
import net.blueshell.clients.listmonk.invoker.ApiClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(ListmonkProperties::class)
class ListmonkConfig {

    @Bean
    @Profile("!test")
    fun listmonkApiClient(props: ListmonkProperties): ApiClient {
        val client = ApiClient()
        client.basePath = props.api.baseUrl
        client.setUsername(props.api.username)
        client.setPassword(props.api.password)
        return client
    }

    @Bean
    @Profile("!test")
    fun listmonkTransactionalApi(client: ApiClient): TransactionalApi = TransactionalApi(client)

    @Bean
    @Profile("!test")
    fun listmonkBouncesApi(client: ApiClient): BouncesApi = BouncesApi(client)
}
