package net.blueshell.api.survey.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.survey.web.dto.QuestionDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Question
import org.springframework.stereotype.Component

@Konverter
interface QuestionKonverter {
    fun toDTO(question: Question): QuestionDTO

    fun fromDTO(dto: QuestionDTO): Question
}

@Component
class QuestionMapper : BaseMapper<Question, QuestionDTO>() {
    private val konverter = konverter<QuestionKonverter>()

    override fun fromDTO(dto: QuestionDTO): Question = konverter.fromDTO(dto)

    fun fromDTO(dto: QuestionDTO, question: Question): Question {
        return konverter.fromDTO(dto)
    }

    override fun toDTO(entity: Question): QuestionDTO = konverter.toDTO(entity)
}

fun Question.asDTO(mapper: QuestionMapper): QuestionDTO = mapper.toDTO(this)

fun QuestionDTO.asEntity(mapper: QuestionMapper): Question = mapper.fromDTO(this)
