package net.blueshell.api.factory.telemetry.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.telemetry.persistence.Telemetry
import org.springframework.stereotype.Component

@Component
class TelemetryFactory(
    private val persistence: FactoryPersistenceSupport,
) {
    fun build(
        platform: PlatformType = PlatformType.TWITTER,
        url: String = "https://example.com/${System.currentTimeMillis()}",
    ): Telemetry = Telemetry(platform = platform, url = url)

    fun create(
        platform: PlatformType = PlatformType.TWITTER,
        url: String = "https://example.com/${System.currentTimeMillis()}",
    ): Telemetry = persistence.persist(build(platform, url))
}
