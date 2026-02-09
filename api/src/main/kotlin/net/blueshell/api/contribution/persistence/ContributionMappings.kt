package net.blueshell.api.contribution.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.contribution.web.dto.ContributionDTO
import net.blueshell.api.contribution.web.dto.ContributionKonverter
import net.blueshell.api.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.contribution.web.dto.ContributionPeriodKonverter
import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import net.blueshell.api.contribution.web.dto.ContributionReminderKonverter

private val contributionKonverter = Konverter.get<ContributionKonverter>()
private val contributionPeriodKonverter = Konverter.get<ContributionPeriodKonverter>()
private val contributionReminderKonverter = Konverter.get<ContributionReminderKonverter>()

fun Contribution.asDto(): ContributionDTO = contributionKonverter.toDTO(this)

fun ContributionPeriod.asDto(): ContributionPeriodDTO = contributionPeriodKonverter.toDTO(this)

fun ContributionReminder.asDto(): ContributionReminderDTO = contributionReminderKonverter.toDTO(this)
