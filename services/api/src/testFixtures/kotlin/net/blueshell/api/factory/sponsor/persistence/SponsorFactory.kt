package net.blueshell.api.factory.sponsor.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.file.persistence.File
import net.blueshell.api.sponsor.persistence.Sponsor
import org.springframework.stereotype.Component

@Component
class SponsorFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun build(
        name: String = "Sponsor ${System.currentTimeMillis()}",
        picture: File? = null
    ): Sponsor {
        return Sponsor(
            name = name,
            description = "Sponsor description"
        ).apply {
            if (picture != null) {
                this.picture = picture
            }
        }
    }

    fun create(
        name: String = "Sponsor ${System.currentTimeMillis()}",
        picture: File? = null
    ): Sponsor {
        return persistence.persist(build(name, picture))
    }
}
