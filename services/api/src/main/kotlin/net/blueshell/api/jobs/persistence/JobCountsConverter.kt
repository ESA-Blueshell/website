package net.blueshell.api.jobs.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobTally

/**
 * Counts as `TALLY,COUNT,CHANNEL|CHANNEL,NAME|NAME` entries joined by `;`, as folded triggers are
 * kept on the row; an entry written before names were kept ends after its channels. A name holds
 * none of `,|;`, as a shortcode's name cannot.
 */
@Converter
class JobCountsConverter : AttributeConverter<List<JobCount>?, String?> {
    override fun convertToDatabaseColumn(attribute: List<JobCount>?): String? =
        attribute
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(";") { "${it.tally},${it.count},${it.channels.joinToString("|")},${it.names.joinToString("|")}" }

    override fun convertToEntityAttribute(dbData: String?): List<JobCount> =
        dbData.orEmpty().split(";").filter { it.isNotBlank() }.map { entry ->
            val (tally, count, listedAfter) = entry.split(",", limit = 3)
            val channels = listed(listedAfter.substringBefore(','))
            JobCount(JobTally.valueOf(tally), count.toLong(), channels, listed(listedAfter.substringAfter(',', "")))
        }
}

private fun listed(joined: String) = joined.split("|").filter { it.isNotBlank() }
