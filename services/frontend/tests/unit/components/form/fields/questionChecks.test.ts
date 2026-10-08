import {describe, expect, it} from "vitest"
import {questionFields} from "@/components/form/fields/questionChecks"
import {useFormChecks} from "@/composables/useFormChecks"
import {QuestionType, type SurveyRequest} from "@/domains/events"

describe("questionFields", () => {
  it("names each question's text and each option of a choice by the api's paths", () => {
    const survey: SurveyRequest = {questions: [
      {idx: 0, type: QuestionType.DESCRIPTION, label: "Bring a controller."},
      {idx: 1, type: QuestionType.RADIO, label: "Pick", choiceLabels: ["A", "B"]},
    ]}

    expect(Object.keys(questionFields(() => survey))).toEqual([
      "signUpForm.questions[0].label",
      "signUpForm.questions[1].label",
      "signUpForm.questions[1].choiceLabels[0]",
      "signUpForm.questions[1].choiceLabels[1]",
    ])
  })

  it("asks for every text, and keeps an option to a hundred characters", () => {
    const survey: SurveyRequest = {questions: [
      {idx: 0, type: QuestionType.CHECKBOX, label: "", choiceLabels: ["x".repeat(101), "B"]},
    ]}
    const checks = useFormChecks(() => questionFields(() => survey))

    expect(checks.attempt()).toBe(false)
    expect(checks.errorsOf("signUpForm.questions[0].label")).toEqual(["This field is required"])
    expect(checks.errorsOf("signUpForm.questions[0].choiceLabels[0]")).toEqual(["Must be at most 100 characters"])
    expect(checks.errorsOf("signUpForm.questions[0].choiceLabels[1]")).toEqual([])
  })

  it("asks nothing of a form without questions, nor of a choice without options", () => {
    expect(questionFields(() => null)).toEqual({})
    expect(Object.keys(questionFields(() => ({questions: [{idx: 0, type: QuestionType.RADIO, label: "Pick"}]}))))
      .toEqual(["signUpForm.questions[0].label"])
  })
})
