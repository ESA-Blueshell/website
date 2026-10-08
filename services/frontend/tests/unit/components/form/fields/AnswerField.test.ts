import {describe, expect, it} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import {QuestionType} from "@/domains/events"

describe("AnswerField", () => {
  it("says what its form's checks say of it, and tells the form when it is left", async () => {
    const wrapper = mount(AnswerField, {
      props: {
        question: {id: 1, idx: 0, type: QuestionType.OPEN, label: "Why?", required: true},
        errorMessages: ["This field is required"],
      },
    })

    expect(wrapper.get(".answer-field__said").text()).toBe("This field is required")
    await wrapper.get("textarea").trigger("blur")
    expect(wrapper.emitted("blur")).toHaveLength(1)
  })

  it("writes what is typed into an open answer", async () => {
    const typed = {questionId: 1, textResponse: ""}
    const wrapper = mount(AnswerField, {
      props: {question: {id: 1, idx: 0, type: QuestionType.OPEN, label: "Why?"}, modelValue: typed},
    })

    await wrapper.get("textarea").setValue("Because")

    expect(typed.textResponse).toBe("Because")
  })

  it("offers a radio question's choices and records the one picked", async () => {
    const picked = {questionId: 3, textResponse: "", optionSelections: [false, false]}
    const wrapper = mount(AnswerField, {
      props: {
        question: {id: 3, idx: 2, type: QuestionType.RADIO, label: "Which?", choiceLabels: ["Tea", "Coffee"], required: true},
        modelValue: picked,
      },
    })
    const radio = wrapper.getComponent({name: "RadioGroup"})
    expect(radio.props("options")).toEqual([{key: "0", label: "Tea"}, {key: "1", label: "Coffee"}])

    radio.vm.$emit("update:modelValue", "1")
    await flushPromises()

    expect(picked.optionSelections).toEqual([false, true])
  })

  it("records each ticked choice of a checkbox question", async () => {
    const ticked = {questionId: 4, textResponse: "", optionSelections: [false, false]}
    const wrapper = mount(AnswerField, {
      props: {
        question: {id: 4, idx: 3, type: QuestionType.CHECKBOX, label: "Which?", choiceLabels: ["A", "B"], required: false},
        modelValue: ticked,
      },
    })
    const boxes = wrapper.findAllComponents({name: "CheckBox"})
    expect(boxes.map(box => box.props("label"))).toEqual(["A", "B"])

    boxes[1]!.vm.$emit("update:modelValue", true)
    await flushPromises()

    expect(ticked.optionSelections).toEqual([false, true])
  })

  it("tells its form when a choice is left", async () => {
    const radio = mount(AnswerField, {
      props: {question: {id: 3, idx: 2, type: QuestionType.RADIO, label: "Which?", choiceLabels: ["Tea", "Coffee"]}},
    })
    await radio.getComponent({name: "RadioGroup"}).trigger("focusout")
    expect(radio.emitted("blur")).toHaveLength(1)

    const boxes = mount(AnswerField, {
      props: {question: {id: 4, idx: 3, type: QuestionType.CHECKBOX, label: "Which?", choiceLabels: ["A", "B"]}},
    })
    await boxes.getComponent({name: "CheckBox"}).trigger("focusout")
    expect(boxes.emitted("blur")).toHaveLength(1)
  })
})
