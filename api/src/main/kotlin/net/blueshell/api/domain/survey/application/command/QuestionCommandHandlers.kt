package net.blueshell.api.domain.survey.application.command

import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.application.SurveyService
import net.blueshell.api.domain.survey.command.*
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateQuestionHandler(
    private val service: QuestionService,
    private val surveyService: SurveyService
) : CommandHandler<CreateQuestionCommand, Question> {
    override val commandType = CreateQuestionCommand::class

    override fun handle(command: CreateQuestionCommand): Question {
        val question = Question()
        question.idx = command.idx
        question.type = command.type
        question.label = command.label
        question.choiceLabels = command.choiceLabels
        question.survey = surveyService.findById(command.surveyId)
        return service.create(question)
    }
}

@Component
class UpdateQuestionHandler(
    private val service: QuestionService,
    private val surveyService: SurveyService
) : CommandHandler<UpdateQuestionCommand, Question> {
    override val commandType = UpdateQuestionCommand::class

    override fun handle(command: UpdateQuestionCommand): Question {
        var question = service.findById(command.id)
        question.idx = command.idx
        question.type = command.type
        question.label = command.label
        question.choiceLabels = command.choiceLabels
        question.survey = surveyService.findById(command.surveyId)
        return service.update(question)
    }
}

@Component
class FindQuestionsHandler(
    private val service: QuestionService
) : CommandHandler<FindQuestionsCommand, MutableList<Question>> {
    override val commandType = FindQuestionsCommand::class

    override fun handle(command: FindQuestionsCommand): MutableList<Question> {
        return service.findAll()
    }
}

@Component
class FindQuestionByIdHandler(
    private val service: QuestionService
) : CommandHandler<FindQuestionByIdCommand, Question> {
    override val commandType = FindQuestionByIdCommand::class

    override fun handle(command: FindQuestionByIdCommand): Question {
        return service.findById(command.id)
    }
}

@Component
class DeleteQuestionByIdHandler(
    private val service: QuestionService
) : CommandHandler<DeleteQuestionByIdCommand, Unit> {
    override val commandType = DeleteQuestionByIdCommand::class

    override fun handle(command: DeleteQuestionByIdCommand) {
        service.deleteById(command.id)
    }
}
