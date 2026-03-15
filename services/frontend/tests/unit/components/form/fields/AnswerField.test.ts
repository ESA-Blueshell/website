import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import {QuestionType} from "@/services/api"

describe("AnswerField", () => {
  it("uses required-text validator with the expected message for open questions", () => {
    const wrapper = shallowMount(AnswerField, {
      props: {
        question: {
          id: 1,
          idx: 0,
          type: QuestionType.OPEN,
          label: "Why?",
        },
      },
    })

    const field = wrapper.findComponent({name: "Field"})
    const rule = field.props("rules") as (value: string) => true | string

    expect(rule("")).toBe("This field is required")
    expect(rule("valid")).toBe(true)
  })

  it("uses option-selection validator with the expected message for checkbox questions", () => {
    const wrapper = shallowMount(AnswerField, {
      props: {
        question: {
          id: 2,
          idx: 1,
          type: QuestionType.CHECKBOX,
          label: "Pick one",
          choiceLabels: ["A", "B"],
        },
      },
    })

    const field = wrapper.findComponent({name: "Field"})
    const rule = field.props("rules") as (value: boolean[]) => true | string

    expect(rule([false, false])).toBe("Select at least one option")
    expect(rule([true, false])).toBe(true)
  })
})
