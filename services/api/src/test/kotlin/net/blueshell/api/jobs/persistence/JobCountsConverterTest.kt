package net.blueshell.api.jobs.persistence

import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobTally
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JobCountsConverterTest {
    private val converter = JobCountsConverter()

    @Test
    fun `keeps counts and the channels and names they hold across a save`() {
        val counts =
            listOf(
                JobCount(JobTally.PLACES_READ, 2, listOf("1001", "1002")),
                JobCount(JobTally.SHORTCODES_LEFT, 2, names = listOf("nope", "+1")),
            )

        val column = converter.convertToDatabaseColumn(counts)

        assertThat(column).isEqualTo("PLACES_READ,2,1001|1002,;SHORTCODES_LEFT,2,,nope|+1")
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(counts)
    }

    @Test
    fun `reads an entry written before names were kept`() {
        assertThat(converter.convertToEntityAttribute("PLACES_READ,2,1001|1002;MESSAGES_READ,1902,")).containsExactly(
            JobCount(JobTally.PLACES_READ, 2, listOf("1001", "1002")),
            JobCount(JobTally.MESSAGES_READ, 1902),
        )
    }

    @Test
    fun `keeps no counts as no column`() {
        assertThat(converter.convertToDatabaseColumn(emptyList())).isNull()
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isEmpty()
    }
}
