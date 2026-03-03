package net.blueshell.api.platform.integration.queue

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.service.EmailService

abstract class AbstractMailJobHandler<T : Any>(
    objectMapper: ObjectMapper,
    payloadType: Class<T>,
    protected val emails: EmailService
) : AbstractJsonJobHandler<T>(objectMapper, payloadType)
