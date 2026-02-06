package net.blueshell.api.mapper

import net.blueshell.api.dto.FileDTO
import net.blueshell.api.mapper.base.BaseMapper
import net.blueshell.api.model.File
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
