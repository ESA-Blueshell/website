package net.blueshell.api.domain.survey.application.command

import net.blueshell.api.domain.survey.application.SurveyService
import net.blueshell.api.domain.survey.command.*
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.survey.web.mapping.asEntity
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateSurveyHandler(
    private val service: SurveyService
) : CommandHandler<CreateSurveyCommand, Survey> {
    override val commandType = CreateSurveyCommand::class

    override fun handle(command: CreateSurveyCommand): Survey {
        var survey = command.dto.asEntity()
        survey = service.create(survey)
        return survey
    }
}

@Component
class UpdateSurveyHandler(
    private val service: SurveyService
) : CommandHandler<UpdateSurveyCommand, Survey> {
    override val commandType = UpdateSurveyCommand::class

    override fun handle(command: UpdateSurveyCommand): Survey {
        var survey = service.findById(command.id)
        command.dto.asEntity(survey)
        survey = service.update(survey)
        return survey
    }
}

@Component
class FindSurveysHandler(
    private val service: SurveyService
) : CommandHandler<FindSurveysCommand, MutableList<Survey>> {
    override val commandType = FindSurveysCommand::class

    override fun handle(command: FindSurveysCommand): MutableList<Survey> {
        return service.findAll()
    }
}

@Component
class FindSurveyByIdHandler(
    private val service: SurveyService
) : CommandHandler<FindSurveyByIdCommand, Survey> {
    override val commandType = FindSurveyByIdCommand::class

    override fun handle(command: FindSurveyByIdCommand): Survey {
        return service.findById(command.id)
    }
}

@Component
class DeleteSurveyByIdHandler(
    private val service: SurveyService
) : CommandHandler<DeleteSurveyByIdCommand, Unit> {
    override val commandType = DeleteSurveyByIdCommand::class

    override fun handle(command: DeleteSurveyByIdCommand) {
        service.deleteById(command.id)
    }
}
