package net.blueshell.api.survey.repository

import net.blueshell.api.survey.model.Survey
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface SurveyRepository : BaseRepository<Survey, Long>