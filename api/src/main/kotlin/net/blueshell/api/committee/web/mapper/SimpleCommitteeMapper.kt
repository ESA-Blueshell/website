package net.blueshell.api.committee.web.mapper

import net.blueshell.api.committee.web.dto.SimpleCommitteeDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.committee.persistence.Committee
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
