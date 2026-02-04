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

    @AfterMapping
    protected fun wireBackRefs(dto: AdvancedCommitteeDTO, @MappingTarget committee: Committee) {
        log.info("Wiring back refs for committee {}", committee)
        log.info("dto: {}", dto)
        log.info("Wiring members for committee {}", committee.members)
        val committeeId = committee.id
        for (m in committee.members) {
            log.info("Member of committee member: {}", m)
            if (committeeId != null) {
                m.committeeId = committeeId
            }
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "members")
    @Mapping(target = "version")
    abstract override fun toDTO(committee: Committee): AdvancedCommitteeDTO

    companion object {
        private val log = LoggerFactory.getLogger(AdvancedCommitteeMapper::class.java)
    }
}
