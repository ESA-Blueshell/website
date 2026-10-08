import {describe, expect, it} from "vitest"
import {mount, shallowMount} from "@vue/test-utils"
import AnswersForm from "@/components/form/AnswersForm.vue"
import {QuestionType} from "@/domains/events"

describe("AnswersForm", () => {
  it("renders answer fields for non-description questions only", () => {
    const wrapper = shallowMount(AnswersForm, {
      props: {
        survey: {
          questions: [
            {id: 1, idx: 0, type: QuestionType.DESCRIPTION, label: "**Intro**"},
            {id: 2, idx: 1, type: QuestionType.OPEN, label: "Open"},
            {id: 3, idx: 2, type: QuestionType.CHECKBOX, label: "Choices", choiceLabels: ["A", "B"]},
          ],
        },
      },
      global: {
        stubs: {
          AnswerField: true,
          QuestionCard: {template: "<div><slot /></div>"},
          QuestionLabel: true,
          MarkdownView: false,
        },
      },
    })

    expect(wrapper.findAll("answer-field-stub")).toHaveLength(2)
    expect(wrapper.get(".answers-form__description strong").text()).toBe("Intro")
  })

  it("checks each answer by its question, and shows what is wrong once a save is tried", async () => {
    const wrapper = mount(AnswersForm, {
      props: {
        survey: {
          questions: [
            {id: 1, idx: 0, type: QuestionType.DESCRIPTION, label: "Intro"},
            {id: 2, idx: 1, type: QuestionType.OPEN, label: "Open", required: true},
            {id: 3, idx: 2, type: QuestionType.CHECKBOX, label: "Choices", choiceLabels: ["A", "B"], required: false},
          ],
        },
      },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.find(".answer-field__said").exists()).toBe(false)

    expect(await (wrapper.vm as any).validate()).toBe(false)
    await wrapper.vm.$nextTick()

    expect(wrapper.findAll(".answer-field__said").map(said => said.text())).toEqual(["This field is required"])
  })

  it("shows a required answer missing once it is left", async () => {
    const wrapper = mount(AnswersForm, {
      props: {survey: {questions: [{id: 2, idx: 0, type: QuestionType.OPEN, label: "Open", required: true}]}},
    })
    await wrapper.vm.$nextTick()

    await wrapper.getComponent({name: "AnswerField"}).vm.$emit("blur")

    expect(wrapper.get(".answer-field__said").text()).toBe("This field is required")
  })
})
