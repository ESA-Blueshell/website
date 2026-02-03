package net.blueshell.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "storage")
class StorageConfig {
    var location: String? = null
}
