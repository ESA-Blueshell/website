package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ImapBounceProperties::class)
class ImapBounceConfig
