import {describe, expect, it} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import SurveyForm from "@/components/form/SurveyForm.vue"
import "@/plugins/validation"
import {QuestionType, type SurveyRequest} from "@/domains/events"

const mountSurvey = (survey: SurveyRequest = {questions: []}) => {
  const wrapper: VueWrapper = mount(SurveyForm, {
    props: {
      modelValue: survey,
      "onUpdate:modelValue": (next: SurveyRequest) => wrapper.setProps({modelValue: next}),
    },
  })
  return wrapper
}

const held = (wrapper: VueWrapper): SurveyRequest => wrapper.props("modelValue") as SurveyRequest
const press = (wrapper: VueWrapper, label: string, at = 0) =>
  wrapper.findAll(`button[aria-label="${label}"]`)[at]!.trigger("click")

describe("SurveyForm", () => {
  it("says there are no questions yet", () => {
    expect(mountSurvey().text()).toContain("No questions yet.")
  })

  it("adds one question of each type, required where an answer is expected", async () => {
    const wrapper = mountSurvey()

    await press(wrapper, "Add a description")
    await press(wrapper, "Add an open question")
    await press(wrapper, "Add a multiple choice question")
    await press(wrapper, "Add a checkboxes question")

    expect(held(wrapper).questions).toEqual([
      {idx: 0, type: QuestionType.DESCRIPTION, label: "", required: false},
      {idx: 1, type: QuestionType.OPEN, label: "", required: true},
      {idx: 2, type: QuestionType.RADIO, label: "", required: true, choiceLabels: ["", ""]},
      {idx: 3, type: QuestionType.CHECKBOX, label: "", required: false, choiceLabels: ["", ""]},
    ])
    expect(wrapper.findAllComponents({name: "QuestionEditor"})).toHaveLength(4)
  })

  it("moves, edits and deletes questions, keeping them numbered in order", async () => {
    const wrapper = mountSurvey({
      questions: [
        {idx: 0, type: QuestionType.OPEN, label: "Q1"},
        {idx: 1, type: QuestionType.OPEN, label: "Q2"},
        {idx: 2, type: QuestionType.OPEN, label: "Q3"},
      ],
    })

    await press(wrapper, "Move question down", 0)
    expect(held(wrapper).questions.map(q => q.label)).toEqual(["Q2", "Q1", "Q3"])

    await press(wrapper, "Move question up", 2)
    expect(held(wrapper).questions.map(q => q.label)).toEqual(["Q2", "Q3", "Q1"])

    await wrapper.findAll("textarea")[1]!.setValue("Q3 again")
    expect(held(wrapper).questions[1]!.label).toBe("Q3 again")

    await press(wrapper, "Delete question", 0)
    expect(held(wrapper).questions).toEqual([
      {idx: 0, type: QuestionType.OPEN, label: "Q3 again"},
      {idx: 1, type: QuestionType.OPEN, label: "Q1"},
    ])
  })
})
