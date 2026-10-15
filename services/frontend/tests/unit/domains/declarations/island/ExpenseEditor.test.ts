import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import ExpenseEditor from "@/domains/declarations/island/ExpenseEditor.vue"
import {BookingKind, type Expense} from "@/domains/declarations/declarations"

const ReceiptPicker = {name: "ReceiptPicker", props: ["modelValue", "invalid", "wrong", "testid"], emits: ["update:modelValue"], template: "<div />"}
const SegmentedChoice = {name: "SegmentedChoice", props: ["modelValue", "options", "testidPrefix"], emits: ["update:modelValue"], template: "<div />"}
const SearchPicker = {name: "SearchPicker", props: ["options", "selectedKey", "emptyNote", "controlId", "labelledBy", "testidPrefix"], emits: ["pick"], template: "<div />"}

const blank: Expense = {id: "a", paidOn: "", description: "", amount: "", for: null, receipts: []}

const editor = (props: Record<string, unknown> = {}) => mount(ExpenseEditor, {
  props: {
    modelValue: blank,
    number: 1,
    removable: true,
    testid: "e0",
    committees: [{id: 3, name: "Promo Committee"}],
    events: [{id: 7, name: "Autumn LAN party", note: "1 Oct 2026"}],
    ...props,
  },
  global: {stubs: {ReceiptPicker, SegmentedChoice, SearchPicker}},
})

const last = (wrapper: ReturnType<typeof editor>) => wrapper.emitted("update:modelValue")!.at(-1)![0] as Expense

describe("the expense editor", () => {
  it("writes each field into the expense and says when one is left", async () => {
    const wrapper = editor({errors: {receipts: ["Add at least one receipt."]}})

    await wrapper.get("[data-testid=e0-description] textarea").setValue("Pizza")
    expect(last(wrapper).description).toBe("Pizza")
    await wrapper.get("[data-testid=e0-description] textarea").trigger("blur")
    expect(wrapper.emitted("touch")).toContainEqual(["description"])
    wrapper.getComponent(ReceiptPicker).vm.$emit("update:modelValue", [{id: 1}])
    expect(last(wrapper).receipts).toEqual([{id: 1}])
    expect(wrapper.getComponent(ReceiptPicker).props("wrong")).toBe("Add at least one receipt.")
    expect(wrapper.get(".expense__label--wrong").text()).toContain("Receipts")

    for (const [testid, field, value] of [["e0-day", "paidOn", "2026-10-01"], ["e0-amount", "amount", "12.50"]] as const) {
      const control = wrapper.findAllComponents({name: "FormControl"}).find(one => one.props("testid") === testid)!
      control.vm.$emit("update:modelValue", value)
      expect(last(wrapper)[field]).toBe(value)
      control.vm.$emit("update:modelValue", null)
      expect(last(wrapper)[field]).toBe("")
      control.vm.$emit("blur")
      expect(wrapper.emitted("touch")).toContainEqual([field])
    }
    wrapper.findAllComponents({name: "FormControl"}).find(one => one.props("testid") === "e0-description")!.vm.$emit("update:modelValue", null)
    expect(last(wrapper).description).toBe("")

    await wrapper.get("[data-testid=e0-remove]").trigger("click")
    expect(wrapper.emitted("remove")).toHaveLength(1)
    expect(editor({removable: false, errors: undefined}).find("[data-testid=e0-remove]").exists()).toBe(false)
  })

  it("books an expense to a committee or an event picked from its own list, or to neither", async () => {
    const wrapper = editor()
    expect(wrapper.findComponent(SearchPicker).exists()).toBe(false)

    wrapper.getComponent(SegmentedChoice).vm.$emit("update:modelValue", BookingKind.EVENT)
    await wrapper.vm.$nextTick()
    expect(wrapper.getComponent(SearchPicker).props("options")).toEqual([{key: "7", label: "Autumn LAN party", note: "1 Oct 2026"}])
    wrapper.getComponent(SearchPicker).vm.$emit("pick", "7")
    expect(last(wrapper).for).toEqual({kind: BookingKind.EVENT, id: 7, name: "Autumn LAN party"})

    wrapper.getComponent(SegmentedChoice).vm.$emit("update:modelValue", BookingKind.COMMITTEE)
    await wrapper.vm.$nextTick()
    expect(wrapper.getComponent(SearchPicker).props("options")).toEqual([{key: "3", label: "Promo Committee"}])
    wrapper.getComponent(SearchPicker).vm.$emit("pick", "3")
    expect(last(wrapper).for).toEqual({kind: BookingKind.COMMITTEE, id: 3, name: "Promo Committee"})
    wrapper.getComponent(SearchPicker).vm.$emit("pick", "99")

    const booked = editor({modelValue: {...blank, for: {kind: BookingKind.EVENT, id: 7, name: "Autumn LAN party"}}})
    expect(booked.getComponent(SegmentedChoice).props("modelValue")).toBe(BookingKind.EVENT)
    expect(booked.getComponent(SearchPicker).props("selectedKey")).toBe("7")
  })
})
