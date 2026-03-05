package net.blueshell.api.platform.integration.queue

import tools.jackson.databind.ObjectMapper
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService

abstract class AbstractMailJobHandler<T : Any>(
    objectMapper: ObjectMapper,
    payloadType: Class<T>,
    protected val emails: EmailSenderService
) : AbstractJsonJobHandler<T>(objectMapper, payloadType)
