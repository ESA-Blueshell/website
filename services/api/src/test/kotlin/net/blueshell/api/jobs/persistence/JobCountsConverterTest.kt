package net.blueshell.api.jobs.persistence

import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobTally
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JobCountsConverterTest {
    private val converter = JobCountsConverter()

    @Test
    fun `keeps counts and the channels they name across a save`() {
        val counts =
            listOf(
                JobCount(JobTally.PLACES_READ, 2, listOf("1001", "1002")),
                JobCount(JobTally.MESSAGES_READ, 1902),
            )

        val column = converter.convertToDatabaseColumn(counts)

        assertThat(column).isEqualTo("PLACES_READ,2,1001|1002;MESSAGES_READ,1902,")
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(counts)
    }

    @Test
    fun `keeps no counts as no column`() {
        assertThat(converter.convertToDatabaseColumn(emptyList())).isNull()
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isEmpty()
    }
}
