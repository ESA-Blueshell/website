package net.blueshell.api.committee.api.mapper

import net.blueshell.api.committee.api.dto.AdvancedCommitteeDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.committee.domain.model.Committee
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring", uses = [CommitteeMemberMapper::class])
abstract class AdvancedCommitteeMapper : BaseMapper<Committee, AdvancedCommitteeDTO>() {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: AdvancedCommitteeDTO, @MappingTarget committee: Committee): Committee

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    @Mapping(target = "version")
    abstract override fun toDTO(committee: Committee): AdvancedCommitteeDTO
}
