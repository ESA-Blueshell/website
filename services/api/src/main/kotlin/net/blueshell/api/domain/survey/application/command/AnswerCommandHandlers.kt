package net.blueshell.api.domain.survey.application.command

import net.blueshell.api.domain.survey.application.AnswerService
import net.blueshell.api.domain.survey.application.QuestionService
import net.blueshell.api.domain.survey.command.*
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateAnswerHandler(
    private val service: AnswerService,
    private val questionService: QuestionService
) : CommandHandler<CreateAnswerCommand, Answer> {
    override val commandType = CreateAnswerCommand::class

    override fun handle(command: CreateAnswerCommand): Answer {
        val answer = Answer(
            question = questionService.findById(command.questionId),
            optionSelections = command.optionSelections,
            textResponse = command.textResponse,
        )
        return service.create(answer)
    }
}

@Component
class UpdateAnswerHandler(
    private val service: AnswerService,
    private val questionService: QuestionService
) : CommandHandler<UpdateAnswerCommand, Answer> {
    override val commandType = UpdateAnswerCommand::class

    override fun handle(command: UpdateAnswerCommand): Answer {
        var answer = service.findById(command.id)
        answer.optionSelections = command.optionSelections
        answer.textResponse = command.textResponse
        answer.question = questionService.findById(command.questionId)
        return service.update(answer)
    }
}

@Component
class FindAnswersHandler(
    private val service: AnswerService
) : CommandHandler<FindAnswersCommand, MutableList<Answer>> {
    override val commandType = FindAnswersCommand::class

    override fun handle(command: FindAnswersCommand): MutableList<Answer> {
        return service.findAll()
    }
}

@Component
class FindAnswerByIdHandler(
    private val service: AnswerService
) : CommandHandler<FindAnswerByIdCommand, Answer> {
    override val commandType = FindAnswerByIdCommand::class

    override fun handle(command: FindAnswerByIdCommand): Answer {
        return service.findById(command.id)
    }
}

@Component
class FindAnswersBySurveyIdHandler(
    private val service: AnswerService
) : CommandHandler<FindAnswersBySurveyIdCommand, MutableSet<Answer>> {
    override val commandType = FindAnswersBySurveyIdCommand::class

    override fun handle(command: FindAnswersBySurveyIdCommand): MutableSet<Answer> {
        return service.findBySurveyId(command.surveyId)
    }
}

@Component
class FindAnswersByQuestionIdHandler(
    private val service: AnswerService
) : CommandHandler<FindAnswersByQuestionIdCommand, MutableSet<Answer>> {
    override val commandType = FindAnswersByQuestionIdCommand::class

    override fun handle(command: FindAnswersByQuestionIdCommand): MutableSet<Answer> {
        return service.findByQuestionId(command.questionId)
    }
}

@Component
class DeleteAnswerByIdHandler(
    private val service: AnswerService
) : CommandHandler<DeleteAnswerByIdCommand, Unit> {
    override val commandType = DeleteAnswerByIdCommand::class

    override fun handle(command: DeleteAnswerByIdCommand) {
        service.deleteById(command.id)
    }
}
