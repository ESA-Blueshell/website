package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class EventBannerFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event banner`() {
        val event = persistEvent()
        var bannerFile = fileWithUploader(fileFactory.createImage())
        bannerFile = persist(bannerFile)

        val banner = eventBannerFactory.createBasic()
        banner.event = event
        banner.file = bannerFile

        val saved = persist(banner)
        assertPersisted(net.blueshell.api.model.event.EventBanner::class.java, saved.id)
    }
}
