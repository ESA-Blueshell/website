package net.blueshell.api.survey.persistence

import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface SurveyRepository : BaseRepository<Survey, Long>