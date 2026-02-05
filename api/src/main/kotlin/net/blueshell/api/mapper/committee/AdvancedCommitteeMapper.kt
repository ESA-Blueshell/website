package net.blueshell.api.mapper.committee

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO
import net.blueshell.api.model.committee.Committee
import org.mapstruct.*
import org.slf4j.LoggerFactory

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
