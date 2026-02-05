package net.blueshell.api.repository.survey

import net.blueshell.api.repository.base.BaseRepository
import net.blueshell.api.model.survey.Survey
import org.springframework.stereotype.Repository

@Repository
interface SurveyRepository : BaseRepository<Survey, Long>