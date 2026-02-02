package net.blueshell.api.base

import org.springframework.data.domain.Page
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream

abstract class BaseMapper<T, DTO> : IdentityProvider() {
    abstract fun toDTO(t: T?): DTO?

    abstract fun fromDTO(dto: DTO?): T?

    fun toDTOs(stream: Stream<T?>?): Stream<DTO?>? {
        if (stream == null) {
            return null
        }
        return stream.map<DTO?> { t: T? -> this.toDTO(t) }
    }

    fun toDTOs(list: MutableList<T?>?): MutableList<DTO?>? {
        if (list == null) {
            return null
        }
        return list.stream().map<DTO?> { t: T? -> this.toDTO(t) }.toList()
    }

    fun fromDTOs(dtos: Stream<DTO?>?): Stream<T?>? {
        if (dtos == null) {
            return null
        }
        return dtos.map<T?> { dto: DTO? -> this.fromDTO(dto) }
    }

    fun fromDTOs(dtos: MutableList<DTO?>): MutableList<T?> {
        return dtos.stream().map<T?> { dto: DTO? -> this.fromDTO(dto) }.collect(Collectors.toList())
    }

    fun toDTOs(list: Page<T?>?): Page<DTO?>? {
        if (list == null) {
            return null
        }
        return list.map<DTO?>(Function { t: T? -> this.toDTO(t) })
    }

    fun fromDTOs(dtos: Page<DTO?>?): Page<T?>? {
        if (dtos == null) {
            return null
        }
        return dtos.map<T?>(Function { dto: DTO? -> this.fromDTO(dto) })
    }
}
