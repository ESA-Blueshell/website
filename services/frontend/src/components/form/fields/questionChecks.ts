import {type QuestionRequest, QuestionType, type SurveyRequest} from "@/domains/events"
import type {FieldChecks, FormChecks} from "@/composables/useFormChecks"
import {maxChars, required} from "@/utils/checks"

/** What a question editor shows of its form's checks; a survey edited without a form shows none. */
export type QuestionChecks = Pick<FormChecks, "errorsOf" | "touch">

export const NO_CHECKS: QuestionChecks = {errorsOf: () => [], touch: () => {}}

// The api's own paths, so a refusal it pins on a question lands on that question.
export const labelField = (idx: number): string => `signUpForm.questions[${idx}].label`
export const choiceField = (idx: number, j: number): string => `signUpForm.questions[${idx}].choiceLabels[${j}]`

const asksChoices = (question: QuestionRequest): boolean =>
  question.type === QuestionType.RADIO || question.type === QuestionType.CHECKBOX

/** The checks of every question a sign-up form asks: its text, and each option of a choice. */
export function questionFields(survey: () => SurveyRequest | null | undefined): Record<string, FieldChecks> {
  const questions = survey()?.questions ?? []
  return Object.fromEntries(questions.flatMap(question => [
    [labelField(question.idx), {value: () => question.label, checks: [required]}],
    ...(asksChoices(question) ? question.choiceLabels ?? [] : []).map((choice, j) => [
      choiceField(question.idx, j),
      {value: () => choice, checks: [required, maxChars(100)]},
    ]),
  ]))
}
