import {describe, expect, it} from "vitest"
import {nextTick} from "vue"
import {shallowMount} from "@vue/test-utils"
import SurveyForm from "@/components/form/SurveyForm.vue"
import {QuestionType} from "@/services/api"

describe("SurveyForm", () => {
  it("reorders and removes questions through question-field events", async () => {
    const wrapper = shallowMount(SurveyForm, {
      props: {
        modelValue: {
          questions: [
            {idx: 0, type: QuestionType.OPEN, label: "Q1"},
            {idx: 1, type: QuestionType.OPEN, label: "Q2"},
          ],
        },
      },
      global: {
        stubs: {
          QuestionField: true,
        },
      },
    })

    const questions = wrapper.findAllComponents({name: "QuestionField"})
    expect(questions).toHaveLength(2)

    await questions[0]!.vm.$emit("moveDown")
    await nextTick()

    const reordered = wrapper.findAllComponents({name: "QuestionField"})
    expect((reordered[0]!.props("modelValue") as {label: string}).label).toBe("Q2")

    await reordered[0]!.vm.$emit("remove")
    await nextTick()

    const remaining = wrapper.findAllComponents({name: "QuestionField"})
    expect(remaining).toHaveLength(1)
    expect((remaining[0]!.props("modelValue") as {idx: number}).idx).toBe(0)
  })

})
