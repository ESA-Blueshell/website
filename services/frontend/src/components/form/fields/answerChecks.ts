import {type AnswerRequest, type QuestionResponse, QuestionType} from "@/domains/events"
import type {Check} from "@/utils/checks"

const chosen = (answer?: AnswerRequest): number => (answer?.optionSelections ?? []).filter(Boolean).length

const textGiven = (answer?: AnswerRequest): string | null =>
  ((answer?.textResponse ?? "").trim() ? null : "This field is required")

const oneAtMost = (answer?: AnswerRequest): string | null => (chosen(answer) > 1 ? "Select exactly one option" : null)

const oneChosen = (answer?: AnswerRequest): string | null => (chosen(answer) === 0 ? "Select one option" : null)

const anyChosen = (answer?: AnswerRequest): string | null => (chosen(answer) === 0 ? "Select at least one option" : null)

/** What an answer to the question is checked against; an optional question asks nothing of it. */
export function answerChecks(question: QuestionResponse): Check[] {
  const required = question.required === true
  switch (question.type) {
    case QuestionType.OPEN: return required ? [textGiven] : []
    case QuestionType.RADIO: return required ? [oneAtMost, oneChosen] : [oneAtMost]
    case QuestionType.CHECKBOX: return required ? [anyChosen] : []
    default: return []
  }
}
