import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import QuestionResponses from "@/domains/events/island/QuestionResponses.vue"
import type {RosterRow} from "@/domains/events/island/SignUpRoster.vue"
import {type AnswerResponse, QuestionType} from "@/services/api"
import {anAnswer, aQuestion, aSignUp} from "../../helpers/apiFixtures"

const row = (id: number, name: string, answers: Array<Partial<AnswerResponse>>): RosterRow => ({
  signUp: aSignUp({id}),
  person: {name, discord: "", email: "", phoneNumber: ""},
  answers: new Map(answers.map(one => [one.questionId ?? 0, anAnswer(one)])),
})

const choice = (type: QuestionType) => aQuestion({id: 2, idx: 1, type, label: "Food", choiceLabels: ["Pizza", "Pasta"]})
const open = aQuestion()

describe("what everybody answered to one question", () => {
  it("lists each open answer, and why there is none where there is none", () => {
    const wrapper = mount(QuestionResponses, {props: {question: open, rows: [
      row(1, "Alice", [{questionId: 3, textResponse: "No peanuts"}]),
      row(2, "Bob", [{questionId: 3, textResponse: "  "}]),
      row(3, "Cara", []),
      row(4, "Dan", [{questionId: 3, optionSelections: []}]),
    ]}})

    expect(wrapper.get(".question__title").text()).toBe("1. Comment")
    expect(wrapper.get(".question__meta").text()).toBe("1 of 4 answered")
    expect(wrapper.findAll(".question__none").map(one => one.text())).toEqual(["left blank", "not yet answered", "left blank"])
    expect(wrapper.text()).toContain("No peanuts")
  })

  it("tallies a choice beside its grid of ticks, with the totals under it", () => {
    const wrapper = mount(QuestionResponses, {props: {question: choice(QuestionType.CHECKBOX), rows: [
      row(1, "Alice", [{questionId: 2, optionSelections: [true, false]}]),
      row(2, "Bob", [{questionId: 2, optionSelections: [true, true]}]),
      row(3, "Cara", []),
    ]}})

    expect(wrapper.get(".question__meta").text()).toBe("Pick any · 2 of 3 answered")
    expect(wrapper.findAll(".question__count").map(one => one.text())).toEqual(["2", "1"])
    expect(wrapper.findAll(".question__bar span")[1]!.attributes("style")).toContain("width: 50%")
    expect(wrapper.get(".question__rest").text()).toBe("1 not yet answered")
    expect(wrapper.findAll(".radio-table tfoot td").map(one => one.text())).toEqual(["Totals", "2", "1"])
    expect(wrapper.findAll("[data-answer=chosen]")).toHaveLength(3)
    expect(wrapper.findAll("[data-answer=not-chosen]")).toHaveLength(1)
    expect(wrapper.findAll("[data-answer=missing]")).toHaveLength(2)
  })

  it("reads an answer that predates an option, or carries no selections, as no answer at all", () => {
    const wrapper = mount(QuestionResponses, {props: {question: choice(QuestionType.RADIO), rows: [
      row(1, "Alice", [{questionId: 2, optionSelections: [true]}]),
      row(2, "Bob", [{questionId: 2, textResponse: "typed"}]),
    ]}})

    expect(wrapper.get(".question__meta").text()).toBe("Pick one · 0 of 2 answered")
    expect(wrapper.findAll("[data-answer=missing]")).toHaveLength(4)
    expect(wrapper.get("[data-answer=missing]").attributes("title")).toContain("No answer yet")
  })

  it("says nothing about the rest where everybody answered, and tallies nothing out of nothing", () => {
    const wrapper = mount(QuestionResponses, {props: {question: choice(QuestionType.CHECKBOX), rows: [
      row(1, "Alice", [{questionId: 2, optionSelections: [false, false]}]),
    ]}})

    expect(wrapper.find(".question__rest").exists()).toBe(false)
    expect(wrapper.findAll(".question__bar span").every(one => one.attributes("style")?.includes("width: 0%"))).toBe(true)
  })

  it("draws a choice question without options as a question without a tally", () => {
    const wrapper = mount(QuestionResponses, {props: {question: aQuestion({id: 4, idx: 2, type: QuestionType.RADIO, label: "Empty"}), rows: []}})

    expect(wrapper.findAll(".question__tally-row")).toHaveLength(0)
  })
})
