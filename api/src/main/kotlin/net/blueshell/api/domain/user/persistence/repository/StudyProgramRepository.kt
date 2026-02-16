package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.shared.enums.StudyLevel
import net.blueshell.api.domain.user.persistence.StudyProgram
import net.blueshell.api.shared.repository.BaseRepository
import java.util.Optional

interface StudyProgramRepository : BaseRepository<StudyProgram, Long> {
    fun findByNameAndLevel(name: String, level: StudyLevel): Optional<StudyProgram>
}
