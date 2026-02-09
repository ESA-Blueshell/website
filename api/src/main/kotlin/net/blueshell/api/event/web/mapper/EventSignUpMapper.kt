package net.blueshell.api.event.web.mapper

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.event.web.dto.EventSignUpDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.web.mapper.AnswerMapper
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.user.web.mapper.SimpleUserMapper
import org.springframework.stereotype.Component

@Konverter
interface EventSignUpKonverter {
    @Konvert(
        mappings = [
            Mapping(target = "answers", ignore = true),
            Mapping(target = "guest", ignore = true),
            Mapping(target = "user", ignore = true),
        ]
    )
    fun toDTO(signUp: EventSignUp): EventSignUpDTO

    @Konvert(
        mappings = [
            Mapping(target = "answers", ignore = true),
            Mapping(target = "guest", ignore = true),
            Mapping(target = "user", ignore = true),
        ]
    )
    fun fromDTO(dto: EventSignUpDTO): EventSignUp
}

@Component
class EventSignUpMapper(
    private val guestMapper: GuestMapper,
    private val answerMapper: AnswerMapper,
    private val simpleUserMapper: SimpleUserMapper
) : BaseMapper<EventSignUp, EventSignUpDTO>() {
    private val konverter = konverter<EventSignUpKonverter>()

    override fun fromDTO(dto: EventSignUpDTO): EventSignUp = fromDTO(dto, EventSignUp())

    fun fromDTO(dto: EventSignUpDTO, signUp: EventSignUp): EventSignUp {
        val mapped = konverter.fromDTO(dto)
        mapped.eventId?.let { signUp.eventId = it }
        mapped.userId?.let { signUp.userId = it }
        signUp.guest = dto.guest?.let { guestMapper.fromDTO(it) }

        val mappedAnswers = dto.answers.map { answerMapper.fromDTO(it) }
        val answers = signUp.answers as MutableSet<Answer>
        answers.clear()
        answers.addAll(mappedAnswers)

        dto.version?.let { signUp.version = it }
        return signUp
    }

    override fun toDTO(signUp: EventSignUp): EventSignUpDTO {
        val dto = konverter.toDTO(signUp)
        dto.answers = signUp.answers.map { answerMapper.toDTO(it) }.toMutableList()
        dto.guest = signUp.guest?.let { guestMapper.toDTO(it) }
        dto.user = signUp.user?.let { simpleUserMapper.toDTO(it) }
        return dto
    }
}

fun EventSignUp.asDTO(mapper: EventSignUpMapper): EventSignUpDTO = mapper.toDTO(this)

fun EventSignUpDTO.asEntity(mapper: EventSignUpMapper): EventSignUp = mapper.fromDTO(this)
