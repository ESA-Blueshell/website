package net.blueshell.api.base

import lombok.Data
import java.io.Serializable
import java.time.Instant

@Data
abstract class BaseDTO : Serializable {
    private val id: Long? = null
    private val deletedAt: Instant? = null
    private val createdAt: Instant? = null
    private val updatedAt: Instant? = null
    private val version: Long? = null
}
