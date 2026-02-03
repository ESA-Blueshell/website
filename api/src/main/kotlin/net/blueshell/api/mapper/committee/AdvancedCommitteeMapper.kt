package net.blueshell.api.mapper.committee

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO
import net.blueshell.api.model.committee.Committee
import org.mapstruct.*
import org.slf4j.LoggerFactory

@Mapper(componentModel = "spring", uses = [CommitteeMemberMapper::class])
abstract class AdvancedCommitteeMapper : BaseMapper<Committee?, AdvancedCommitteeDTO?>() {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: AdvancedCommitteeDTO?, @MappingTarget committee: Committee?): Committee?

    @AfterMapping
    protected fun wireBackRefs(dto: AdvancedCommitteeDTO?, @MappingTarget committee: Committee) {
        AdvancedCommitteeMapper.log.info("Wiring back refs for committee {}", committee)
        AdvancedCommitteeMapper.log.info("dto: {}", dto)
        if (committee.getMembers() != null) {
            AdvancedCommitteeMapper.log.info("Wiring members for committee {}", committee.getMembers())
            for (m in committee.getMembers()) {
                AdvancedCommitteeMapper.log.info("Member of committee member: {}", m)
                m.setCommittee(committee)
            }
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    @Mapping(target = "version")
    abstract override fun toDTO(committee: Committee?): AdvancedCommitteeDTO?

    companion object {
        private val log = LoggerFactory.getLogger(AdvancedCommitteeMapper::class.java)
    }
}
