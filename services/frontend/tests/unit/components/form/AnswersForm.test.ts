import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import AnswersForm from "@/components/form/AnswersForm.vue"
import {QuestionType} from "@/services/api"

const formStub = {template: "<div><slot /></div>"}

describe("AnswersForm", () => {
  it("renders answer fields for non-description questions only", () => {
    const wrapper = shallowMount(AnswersForm, {
      props: {
        survey: {
          questions: [
            {id: 1, idx: 0, type: QuestionType.DESCRIPTION, label: "Intro"},
            {id: 2, idx: 1, type: QuestionType.OPEN, label: "Open"},
            {id: 3, idx: 2, type: QuestionType.CHECKBOX, label: "Choices", choiceLabels: ["A", "B"]},
          ],
        },
      },
      global: {
        stubs: {
          Form: formStub,
          AnswerField: true,
          QuestionCard: {template: "<div><slot /></div>"},
          QuestionLabel: true,
        },
      },
    })

    expect(wrapper.findAll("answer-field-stub")).toHaveLength(2)
    expect(wrapper.text()).toContain("Intro")
  })
})
