package net.blueshell.api.domain.survey.application.command

import net.blueshell.api.domain.survey.application.AnswerService
import net.blueshell.api.domain.survey.command.*
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.repository.QuestionRepository
import net.blueshell.api.domain.survey.web.mapping.asEntity
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateAnswerHandler(
    private val service: AnswerService,
    private val questionRepository: QuestionRepository
) : CommandHandler<CreateAnswerCommand, Answer> {
    override val commandType = CreateAnswerCommand::class

    override fun handle(command: CreateAnswerCommand): Answer {
        var answer = command.dto.asEntity()
        answer.question = questionRepository.getReferenceById(command.dto.questionId!!)
        answer = service.create(answer)
        return answer
    }
}

@Component
class UpdateAnswerHandler(
    private val service: AnswerService,
    private val questionRepository: QuestionRepository
) : CommandHandler<UpdateAnswerCommand, Answer> {
    override val commandType = UpdateAnswerCommand::class

    override fun handle(command: UpdateAnswerCommand): Answer {
        var answer = service.findById(command.id)
        command.dto.asEntity(answer)
        answer.question = questionRepository.getReferenceById(command.dto.questionId!!)
        answer = service.update(answer)
        return answer
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
