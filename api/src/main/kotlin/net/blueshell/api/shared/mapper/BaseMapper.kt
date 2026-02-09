package net.blueshell.api.shared.mapper

import net.blueshell.api.feature.auth.security.IdentityProvider
import org.springframework.data.domain.Page
import java.util.stream.Stream

abstract class BaseMapper<T, DTO> : IdentityProvider() {
    abstract fun toDTO(entity: T): DTO

    abstract fun fromDTO(dto: DTO): T

    fun toDTOs(stream: Stream<T>): Stream<DTO> {
        return stream.map { this.toDTO(it) }
    }

    fun toDTOs(list: MutableList<T>): MutableList<DTO> {
        return list.map { this.toDTO(it) }.toMutableList()
    }

    fun fromDTOs(dtos: Stream<DTO>): Stream<T> {
        return dtos.map { this.fromDTO(it) }
    }

    fun fromDTOs(dtos: MutableList<DTO>): MutableList<T> {
        return dtos.map { this.fromDTO(it) }.toMutableList()
    }

    fun toDTOs(list: Page<T>): Page<DTO> {
        return list.map { this.toDTO(it) }
    }

    fun fromDTOs(dtos: Page<DTO>): Page<T> {
        return dtos.map { this.fromDTO(it) }
    }
}