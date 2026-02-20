import {describe, expect, it} from "vitest"
import {shallowMount} from "@vue/test-utils"
import QuestionField from "@/components/form/fields/QuestionField.vue"
import {QuestionType} from "@/services/api"

describe("QuestionField", () => {
  it("uses required validation for the question label", () => {
    const wrapper = shallowMount(QuestionField, {
      props: {
        modelValue: {
          idx: 0,
          label: "",
          type: QuestionType.OPEN,
        },
      },
    })

    const fields = wrapper.findAllComponents({name: "Field"})
    expect(fields).toHaveLength(1)
    expect(fields[0]!.props("rules")).toBe("required")
  })

  it("uses required|maxChars validation for choice labels", () => {
    const wrapper = shallowMount(QuestionField, {
      props: {
        modelValue: {
          idx: 0,
          label: "Pick one",
          type: QuestionType.RADIO,
          choiceLabels: ["A", "B"],
        },
      },
    })

    const rules = wrapper
      .findAllComponents({name: "Field"})
      .map((field) => String(field.props("rules")))
    expect(rules).toContain("required|maxChars:30")
  })
})
