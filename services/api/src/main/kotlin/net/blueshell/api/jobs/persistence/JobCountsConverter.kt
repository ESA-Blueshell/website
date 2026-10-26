package net.blueshell.api.jobs.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobTally

/** Counts as `TALLY,COUNT,CHANNEL|CHANNEL` entries joined by `;`, as folded triggers are kept on the row. */
@Converter
class JobCountsConverter : AttributeConverter<List<JobCount>?, String?> {
    override fun convertToDatabaseColumn(attribute: List<JobCount>?): String? =
        attribute
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(";") { "${it.tally},${it.count},${it.channels.joinToString("|")}" }

    override fun convertToEntityAttribute(dbData: String?): List<JobCount> =
        dbData.orEmpty().split(";").filter { it.isNotBlank() }.map { entry ->
            val (tally, count, channels) = entry.split(",", limit = 3)
            JobCount(JobTally.valueOf(tally), count.toLong(), channels.split("|").filter { it.isNotBlank() })
        }
}
