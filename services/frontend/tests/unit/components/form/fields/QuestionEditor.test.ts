import {describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import QuestionEditor from "@/components/form/fields/QuestionEditor.vue"
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

  it("shows what its form's checks say of each field, and tells the form when one is left", async () => {
    const said: Record<string, string> = {"signUpForm.questions[1].choiceLabels[0]": "This field is required"}
    const checks = {errorsOf: (name: string) => (said[name] ? [said[name]] : []), touch: vi.fn()}
    const wrapper = mountEditor({idx: 1, type: QuestionType.RADIO, label: "Pick", choiceLabels: ["", "B"]}, {checks})

    expect(wrapper.text()).toContain("This field is required")
    await wrapper.findAll("input[type=text]")[1]!.trigger("blur")

    expect(checks.touch).toHaveBeenCalledWith("signUpForm.questions[1].choiceLabels[1]")
  })

  it("marks a multiple choice option with a round glyph", () => {
    const wrapper = mountEditor({idx: 0, type: QuestionType.RADIO, label: "Pick", choiceLabels: ["A", "B"]})

    expect(wrapper.findAll(".question__glyph circle")).toHaveLength(2)
  })

  it("tells its form when the question text is left, and takes it quietly without one", async () => {
    const checks = {errorsOf: () => [], touch: vi.fn()}
    const wrapper = mountEditor({idx: 3, type: QuestionType.OPEN, label: "Q"}, {checks})
    await wrapper.find("textarea").trigger("blur")
    expect(checks.touch).toHaveBeenCalledWith("signUpForm.questions[3].label")

    const alone = mountEditor({idx: 0, type: QuestionType.OPEN, label: "Q"})
    await alone.find("textarea").trigger("blur")
    expect(alone.text()).not.toContain("This field is required")
  })
})
