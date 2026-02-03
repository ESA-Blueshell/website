package net.blueshell.api.mapper.committee

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.committee.SimpleCommitteeDTO
import net.blueshell.api.model.committee.Committee
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
