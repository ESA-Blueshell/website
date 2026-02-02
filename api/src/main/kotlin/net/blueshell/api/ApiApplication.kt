package net.blueshell.api

import net.blueshell.api.config.StorageConfig
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(StorageConfig::class)
@EnableJpaAuditing
@EnableAsync
@EnableRetry
@EnableScheduling
object ApiApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        SpringApplication.run(ApiApplication::class.java, *args)
    }
}
