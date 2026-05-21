import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import {QuestionType} from "@/services/api"

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
})
