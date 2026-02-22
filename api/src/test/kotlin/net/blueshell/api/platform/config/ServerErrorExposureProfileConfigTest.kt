package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties

class ServerErrorExposureProfileConfigTest {

    @Test
    fun `base profile disables server error messages`() {
        assertThat(includeMessage("application.yaml")).isEqualTo("never")
    }

    @Test
    fun `dev profile allows server error messages`() {
        assertThat(includeMessage("application-dev.yaml")).isEqualTo("always")
    }

    @Test
    fun `test profile allows server error messages`() {
        assertThat(includeMessage("application-test.yml")).isEqualTo("always")
    }

    private fun includeMessage(resourceName: String): String? {
        val props = yamlProperties(resourceName)
        return props.getProperty("server.error.include-message")
    }

    private fun yamlProperties(resourceName: String): Properties {
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(ClassPathResource(resourceName))
        return factory.getObject() ?: Properties()
    }
}
