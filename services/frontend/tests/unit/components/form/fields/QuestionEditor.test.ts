import {describe, expect, it} from "vitest"
import {flushPromises, mount, type VueWrapper} from "@vue/test-utils"
import QuestionEditor from "@/components/form/fields/QuestionEditor.vue"
import "@/plugins/validation"
import {type QuestionRequest, QuestionType} from "@/domains/events"

const mountEditor = (question: QuestionRequest, props: Record<string, unknown> = {}) => {
  const wrapper: VueWrapper = mount(QuestionEditor, {
    props: {
      modelValue: question,
      "onUpdate:modelValue": (next: QuestionRequest) => wrapper.setProps({modelValue: next}),
      ...props,
    },
  })
  return wrapper
}

const held = (wrapper: VueWrapper): QuestionRequest => wrapper.props("modelValue") as QuestionRequest
const button = (wrapper: VueWrapper, label: string, at = 0) =>
  wrapper.findAll(`button[aria-label="${label}"]`)[at]!

describe("QuestionEditor", () => {
  it("heads an open question with its number, its type and a Required tick", async () => {
    const wrapper = mountEditor({idx: 2, type: QuestionType.OPEN, label: "What do you play?", required: true})

    expect(wrapper.find(".question__number").text()).toBe("03")
    expect(wrapper.find(".question__type").text()).toBe("Open question")
    const required = wrapper.find<HTMLInputElement>("input[type=checkbox]")
    expect(required.element.checked).toBe(true)

    await required.setValue(false)
    expect(held(wrapper).required).toBe(false)
  })

  it("asks a description for its text alone, with no Required tick", () => {
    const wrapper = mountEditor({idx: 0, type: QuestionType.DESCRIPTION, label: "Bring a controller."})

    expect(wrapper.find(".question__type").text()).toBe("Description")
    expect(wrapper.find("input[type=checkbox]").exists()).toBe(false)
    expect(wrapper.text()).toContain("Description text")
    expect(wrapper.findComponent({name: "MarkdownEditor"}).exists()).toBe(true)
    expect(button(wrapper, "Delete description").exists()).toBe(true)
  })

  it("writes what is typed into the question text", async () => {
    const wrapper = mountEditor({idx: 0, type: QuestionType.OPEN, label: ""})

    await wrapper.find("textarea").setValue("Anything else?")
    expect(held(wrapper).label).toBe("Anything else?")
  })

  it("asks its form to move or delete it, and cannot move past either end", async () => {
    const wrapper = mountEditor({idx: 0, type: QuestionType.OPEN, label: "Q"}, {canMoveUp: false})

    expect(button(wrapper, "Move question up").attributes("disabled")).toBeDefined()
    await button(wrapper, "Move question up").trigger("click")
    await button(wrapper, "Move question down").trigger("click")
    await button(wrapper, "Delete question").trigger("click")

    expect(wrapper.emitted("moveUp")).toBeUndefined()
    expect(wrapper.emitted("moveDown")).toHaveLength(1)
    expect(wrapper.emitted("remove")).toHaveLength(1)
  })

  it("keeps a choice question at two options at least", async () => {
    const wrapper = mountEditor({idx: 0, type: QuestionType.RADIO, label: "Pick", choiceLabels: ["Bike", "Bus"]})

    const remove = button(wrapper, "A choice keeps at least two options")
    expect(remove.attributes("disabled")).toBeDefined()
    await remove.trigger("click")
    expect(held(wrapper).choiceLabels).toEqual(["Bike", "Bus"])
  })

  it("adds, writes, moves and deletes the options of a choice question", async () => {
    const wrapper = mountEditor({idx: 0, type: QuestionType.CHECKBOX, label: "Diet", choiceLabels: ["Vegan", "Halal"]})
    expect(wrapper.findAll(".question__glyph rect")).toHaveLength(2)

    await wrapper.findAll("button").find(one => one.text() === "Add option")!.trigger("click")
    expect(held(wrapper).choiceLabels).toEqual(["Vegan", "Halal", ""])

    await wrapper.findAll("input[type=text]")[2]!.setValue("Kosher")
    expect(held(wrapper).choiceLabels).toEqual(["Vegan", "Halal", "Kosher"])

    await button(wrapper, "Move option down", 0).trigger("click")
    expect(held(wrapper).choiceLabels).toEqual(["Halal", "Vegan", "Kosher"])

    await button(wrapper, "Move option up", 2).trigger("click")
    expect(held(wrapper).choiceLabels).toEqual(["Halal", "Kosher", "Vegan"])

    await button(wrapper, "Delete option", 0).trigger("click")
    expect(held(wrapper).choiceLabels).toEqual(["Kosher", "Vegan"])
  })

  it("says an emptied option needs its text", async () => {
    const wrapper = mountEditor({idx: 0, type: QuestionType.RADIO, label: "Pick", choiceLabels: ["A", "B"]})

    const option = wrapper.findAll("input[type=text]")[0]!
    await option.setValue("")
    await option.trigger("blur")
    await flushPromises()

    expect(wrapper.text()).toContain("This field is required")
  })

  it("marks a multiple choice option with a round glyph", () => {
    const wrapper = mountEditor({idx: 0, type: QuestionType.RADIO, label: "Pick", choiceLabels: ["A", "B"]})

    expect(wrapper.findAll(".question__glyph circle")).toHaveLength(2)
  })

  it("names its fields for the form's validation, as the event form reads them", () => {
    const wrapper = mountEditor({idx: 1, type: QuestionType.RADIO, label: "Pick", choiceLabels: ["A", "B"]})

    const fields = wrapper.findAllComponents({name: "Field"})
    expect(fields.map(field => [field.props("name"), field.props("rules")])).toEqual([
      ["survey.questions[1].label", "required"],
      ["survey.questions[1].choiceLabels[0]", "required|maxChars:100"],
      ["survey.questions[1].choiceLabels[1]", "required|maxChars:100"],
    ])
  })
})
