package net.blueshell.api.repository.survey

import net.blueshell.api.model.survey.Survey
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface SurveyRepository : BaseRepository<Survey, Long>