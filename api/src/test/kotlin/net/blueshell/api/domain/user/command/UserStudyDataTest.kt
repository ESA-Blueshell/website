package net.blueshell.api.domain.user.command

import net.blueshell.api.domain.user.persistence.StudyProgram
import net.blueshell.api.shared.enums.StudyLevel
import net.blueshell.api.shared.enums.StudyStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserStudyDataTest {

    @Test
    fun `asEntity maps all properties`() {
        val data = UserStudyData(
            level = StudyLevel.BSC,
            programName = "Applied Computer Science",
            status = StudyStatus.ONGOING,
            startYear = 2024,
            graduationYear = null
        )

        val entity = data.asEntity { name, level ->
            StudyProgram().apply {
                this.name = name
                this.level = level
            }
        }

        assertThat(entity.studyProgram.name).isEqualTo("Applied Computer Science")
        assertThat(entity.studyProgram.level).isEqualTo(StudyLevel.BSC)
        assertThat(entity.status).isEqualTo(StudyStatus.ONGOING)
        assertThat(entity.startYear).isEqualTo(2024)
        assertThat(entity.graduationYear).isNull()
    }
}
