package net.blueshell.api.factory.model.event

import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.factory.model.ModelFactoryTestSupport
import org.junit.jupiter.api.Test

class EventBannerFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event banner`() {
        val event = persistEvent()
        var bannerFile = fileWithUploader(fileFactory.createImage())
        bannerFile = persist(bannerFile)

        val banner = eventBannerFactory.createBasic()
        banner.event = event
        banner.id.fileId = bannerFile.id

        val saved = persist(banner)
        assertPersisted(EventBanner::class.java, saved.id)
    }
}
