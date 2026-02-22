package net.blueshell.api.domain.user.web.dto.request

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = UpdateUserRequest::class, name = "user"),
    JsonSubTypes.Type(value = BoardUpdateUserRequest::class, name = "board")
)
sealed interface UpdateUserPayload