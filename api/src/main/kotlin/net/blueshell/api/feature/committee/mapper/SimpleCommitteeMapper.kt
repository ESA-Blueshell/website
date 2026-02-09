package net.blueshell.api.feature.committee.mapper

import net.blueshell.api.feature.committee.dto.SimpleCommitteeDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.feature.committee.model.Committee
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping


@Mapper(componentModel = "spring", uses = [CommitteeMemberMapper::class])
abstract class SimpleCommitteeMapper : BaseMapper<Committee, SimpleCommitteeDTO>() {
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(committee: Committee): SimpleCommitteeDTO
}
