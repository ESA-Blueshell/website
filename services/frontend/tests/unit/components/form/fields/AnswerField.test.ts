import {describe, expect, it} from "vitest"
import {flushPromises, mount, shallowMount} from "@vue/test-utils"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import {QuestionType} from "@/domains/events"

describe("AnswerField", () => {
  it("rejects blank text for a required open question", () => {
    const wrapper = shallowMount(AnswerField, {
      props: {
        question: {
          id: 1,
          idx: 0,
          type: QuestionType.OPEN,
          label: "Why?",
          required: true,
        },
      },
    })

    const field = wrapper.findComponent({name: "Field"})
    const rule = field.props("rules") as (value: string) => true | string

    expect(rule("")).toBe("This field is required")
    expect(rule("valid")).toBe(true)
  })

  it("accepts blank text for an optional open question", () => {
    const wrapper = shallowMount(AnswerField, {
      props: {
        question: {
          id: 1,
          idx: 0,
          type: QuestionType.OPEN,
          label: "Why?",
          required: false,
        },
      },
    })

    const field = wrapper.findComponent({name: "Field"})
    const rule = field.props("rules") as (value: string) => true | string

    expect(rule("")).toBe(true)
    expect(rule("filled")).toBe(true)
  })

  it("requires at least one selection for a required checkbox question", () => {
    const wrapper = shallowMount(AnswerField, {
      props: {
        question: {
          id: 2,
          idx: 1,
          type: QuestionType.CHECKBOX,
          label: "Pick one",
          choiceLabels: ["A", "B"],
          required: true,
        },
      },
    })

    const field = wrapper.findComponent({name: "Field"})
    const rule = field.props("rules") as (value: boolean[]) => true | string

    expect(rule([false, false])).toBe("Select at least one option")
    expect(rule([true, false])).toBe(true)
  })

  it("accepts no selection on an optional checkbox question", () => {
    const wrapper = shallowMount(AnswerField, {
      props: {
        question: {
          id: 2,
          idx: 1,
          type: QuestionType.CHECKBOX,
          label: "Pick any",
          choiceLabels: ["A", "B"],
          required: false,
        },
      },
    })

    const field = wrapper.findComponent({name: "Field"})
    const rule = field.props("rules") as (value: boolean[]) => true | string

    expect(rule([false, false])).toBe(true)
    expect(rule([true, false])).toBe(true)
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
})
