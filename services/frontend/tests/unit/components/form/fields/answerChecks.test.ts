import {describe, expect, it} from "vitest"
import {answerChecks} from "@/components/form/fields/answerChecks"
import {type QuestionResponse, QuestionType} from "@/domains/events"
import {firstFailure} from "@/utils/checks"

const question = (type: QuestionType, required: boolean): QuestionResponse =>
  ({id: 1, idx: 0, type, label: "Q", choiceLabels: ["A", "B"], required})

const failureOf = (asked: QuestionResponse, answer: Record<string, unknown>) =>
  firstFailure({questionId: 1, ...answer}, answerChecks(asked))

describe("answerChecks", () => {
  it("asks a required open question for text, and an optional one for nothing", () => {
    expect(failureOf(question(QuestionType.OPEN, true), {textResponse: "  "})).toBe("This field is required")
    expect(failureOf(question(QuestionType.OPEN, true), {textResponse: "Yes"})).toBeNull()
    expect(failureOf(question(QuestionType.OPEN, false), {textResponse: ""})).toBeNull()
  })

  it("lets a multiple choice answer pick one at most, and a required one exactly one", () => {
    expect(failureOf(question(QuestionType.RADIO, false), {optionSelections: [true, true]})).toBe("Select exactly one option")
    expect(failureOf(question(QuestionType.RADIO, false), {optionSelections: [false, false]})).toBeNull()
    expect(failureOf(question(QuestionType.RADIO, true), {optionSelections: [false, false]})).toBe("Select one option")
    expect(failureOf(question(QuestionType.RADIO, true), {optionSelections: [false, true]})).toBeNull()
  })

  it("asks a required checkboxes question for one tick at least", () => {
    expect(failureOf(question(QuestionType.CHECKBOX, true), {optionSelections: [false, false]})).toBe("Select at least one option")
    expect(failureOf(question(QuestionType.CHECKBOX, true), {optionSelections: [true, false]})).toBeNull()
    expect(failureOf(question(QuestionType.CHECKBOX, false), {optionSelections: []})).toBeNull()
  })

  it("reads a missing answer as an empty one", () => {
    expect(firstFailure(undefined, answerChecks(question(QuestionType.OPEN, true)))).toBe("This field is required")
    expect(firstFailure(undefined, answerChecks(question(QuestionType.CHECKBOX, true)))).toBe("Select at least one option")
  })

  it("asks nothing of a description", () => {
    expect(answerChecks(question(QuestionType.DESCRIPTION, true))).toEqual([])
  })
})
