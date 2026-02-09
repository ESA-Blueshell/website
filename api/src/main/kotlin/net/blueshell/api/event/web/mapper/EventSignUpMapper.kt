package net.blueshell.api.event.web.mapper

import net.blueshell.api.event.web.dto.EventSignUpDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.web.mapper.AnswerMapper
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.user.web.mapper.SimpleUserMapper
import org.springframework.stereotype.Component

@Component
class EventSignUpMapper(
    private val guestMapper: GuestMapper,
    private val answerMapper: AnswerMapper,
    private val simpleUserMapper: SimpleUserMapper
) : BaseMapper<EventSignUp, EventSignUpDTO>() {
    override fun fromDTO(dto: EventSignUpDTO): EventSignUp = fromDTO(dto, EventSignUp())

    fun fromDTO(dto: EventSignUpDTO, signUp: EventSignUp): EventSignUp {
        dto.eventId?.let { signUp.eventId = it }
        dto.userId?.let { signUp.userId = it }
        signUp.guest = dto.guest?.let { guestMapper.fromDTO(it) }

        val mappedAnswers = dto.answers.map { answerMapper.fromDTO(it) }
        val answers = signUp.answers as MutableSet<Answer>
        answers.clear()
        answers.addAll(mappedAnswers)

        dto.version?.let { signUp.version = it }
        return signUp
    }

    override fun toDTO(signUp: EventSignUp): EventSignUpDTO {
        return EventSignUpDTO(
            eventId = signUp.eventId,
            answers = signUp.answers.map { answerMapper.toDTO(it) }.toMutableList(),
            guest = signUp.guest?.let { guestMapper.toDTO(it) },
            user = signUp.user?.let { simpleUserMapper.toDTO(it) },
            userId = signUp.userId
        ).also { dto ->
            dto.id = signUp.id
            dto.version = signUp.version
        }
    }
}

fun EventSignUp.asDTO(mapper: EventSignUpMapper): EventSignUpDTO = mapper.toDTO(this)

fun EventSignUpDTO.asEntity(mapper: EventSignUpMapper): EventSignUp = mapper.fromDTO(this)
