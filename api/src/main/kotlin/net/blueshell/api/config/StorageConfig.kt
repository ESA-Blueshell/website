package net.blueshell.api.config

import lombok.Getter
import lombok.Setter
import org.springframework.boot.context.properties.ConfigurationProperties

@Setter
@Getter
@ConfigurationProperties(prefix = "storage")
class StorageConfig {
    private val location: String? = null
}
