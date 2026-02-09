package net.blueshell.api.file.api.mapper

import net.blueshell.api.file.api.dto.FileDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.file.domain.model.File
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
abstract class FileMapper : BaseMapper<File, FileDTO>() {
    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "mediaType")
    @Mapping(target = "type")
    @Mapping(target = "size")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(file: File): FileDTO
}
