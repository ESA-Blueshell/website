import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import AddSignUpDialog from "@/domains/events/island/AddSignUpDialog.vue"
import {aQuestion, aSignUp, aSurvey, anEvent} from "../../../helpers/apiFixtures"

const {mockAddSignUpAsBoard, guestValid, answersValid} = vi.hoisted(() => ({
  mockAddSignUpAsBoard: vi.fn(),
  guestValid: {value: true},
  answersValid: {value: true},
}))

vi.mock("@/domains/events", () => ({addSignUpAsBoard: mockAddSignUpAsBoard}))

// The island's dialog portals to the body; a stand-in keeps what it holds where it can be read.
const ModalDialog = {
  name: "ModalDialog",
  props: ["open", "title", "testid"],
  emits: ["update:open"],
  template: "<div><h2>{{ title }}</h2><slot /><slot name='footer' /></div>",
}

// The picker reads every account from the api; here it only reports who was picked.
const UserPicker = {
  name: "UserPicker",
  props: ["modelValue", "membersOnly", "errorMessages", "label"],
  emits: ["update:modelValue"],
  template: "<div data-testid='add-signup-account'>{{ errorMessages }}</div>",
}

const GuestForm = {
  name: "GuestForm",
  props: ["modelValue", "force"],
  emits: ["update:modelValue"],
  methods: {validate: async () => guestValid.value},
  template: "<div data-testid='guest-form' />",
}

const AnswersForm = {
  name: "AnswersForm",
  props: ["modelValue", "survey"],
  emits: ["update:modelValue"],
  methods: {validate: async () => answersValid.value},
  template: "<div data-testid='answers-form' />",
}

const gordon = {name: "Guest Gordon", discord: "gordon#0001", email: "gordon@example.com", phoneNumber: "+31612345678"}

const withForm = {signUpForm: aSurvey({questions: [aQuestion({id: 3, label: "Diet"})]})}

const dialog = (over: Parameters<typeof anEvent>[0] = {}) =>
  mount(AddSignUpDialog, {
    props: {modelValue: true, event: anEvent({id: 500, title: "LAN", membersOnly: false, ...over})},
    global: {stubs: {ModalDialog, UserPicker, GuestForm, AnswersForm}},
  })

type Dialog = ReturnType<typeof dialog>

const add = async (wrapper: Dialog) => {
  await wrapper.get("[data-testid=add-signup-confirm-btn]").trigger("click")
  await flushPromises()
}

const asGuest = async (wrapper: Dialog) => {
  await wrapper.get("[data-testid=add-signup-holder-guest]").trigger("click")
  await wrapper.findComponent(GuestForm).vm.$emit("update:modelValue", gordon)
}

describe("AddSignUpDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    guestValid.value = true
    answersValid.value = true
    mockAddSignUpAsBoard.mockResolvedValue({ok: true, saved: aSignUp({id: 8})})
  })

  it("opens asking for an account, with the guest fields away", () => {
    const wrapper = dialog()

    expect(wrapper.text()).toContain("Add sign-up")
    expect(wrapper.get("[data-testid=add-signup-holder-account]").attributes("aria-checked")).toBe("true")
    expect(wrapper.find("[data-testid=add-signup-account]").exists()).toBe(true)
    expect(wrapper.find("[data-testid=guest-form]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=add-signup-notify]").exists()).toBe(false)
  })

  it("adds the account that was picked, reports it and closes", async () => {
    const wrapper = dialog()
    await wrapper.findComponent(UserPicker).vm.$emit("update:modelValue", 9)

    await add(wrapper)

    expect(mockAddSignUpAsBoard).toHaveBeenCalledWith(500, {answers: [], userId: 9}, false)
    expect(wrapper.emitted("added")).toEqual([[aSignUp({id: 8})]])
    expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
  })

  it("asks who the sign-up is for where no account is picked, and adds nothing", async () => {
    const wrapper = dialog()

    await add(wrapper)

    expect(wrapper.get("[data-testid=add-signup-account]").text()).toBe("Pick who the sign-up is for.")
    expect(mockAddSignUpAsBoard).not.toHaveBeenCalled()
  })

  it("adds a guest with the details typed, and emails nobody unless asked", async () => {
    const wrapper = dialog()
    await asGuest(wrapper)

    await add(wrapper)

    expect(mockAddSignUpAsBoard).toHaveBeenCalledWith(500, {answers: [], guest: gordon}, false)
  })

  it("emails the guest where the board ticks the box", async () => {
    const wrapper = dialog()
    await asGuest(wrapper)
    await wrapper.get("[data-testid=add-signup-notify]").setValue(true)

    await add(wrapper)

    expect(mockAddSignUpAsBoard).toHaveBeenCalledWith(500, {answers: [], guest: gordon}, true)
  })

  it("emails no account, even after the box was ticked for a guest", async () => {
    const wrapper = dialog()
    await asGuest(wrapper)
    await wrapper.get("[data-testid=add-signup-notify]").setValue(true)
    await wrapper.get("[data-testid=add-signup-holder-account]").trigger("click")
    await wrapper.findComponent(UserPicker).vm.$emit("update:modelValue", 9)

    await add(wrapper)

    expect(mockAddSignUpAsBoard).toHaveBeenCalledWith(500, {answers: [], userId: 9}, false)
  })

  it("adds nothing while the guest's details are not valid", async () => {
    guestValid.value = false
    const wrapper = dialog()
    await asGuest(wrapper)

    await add(wrapper)

    expect(mockAddSignUpAsBoard).not.toHaveBeenCalled()
  })

  it("asks the event's questions, and sends what the board answered for the person", async () => {
    const wrapper = dialog(withForm)
    await wrapper.findComponent(UserPicker).vm.$emit("update:modelValue", 9)
    const answers = [{questionId: 3, textResponse: "Vegan"}]
    await wrapper.findComponent(AnswersForm).vm.$emit("update:modelValue", answers)

    await add(wrapper)

    expect(mockAddSignUpAsBoard).toHaveBeenCalledWith(500, {answers, userId: 9}, false)
  })

  it("adds nothing while a question is not answered", async () => {
    answersValid.value = false
    const wrapper = dialog(withForm)
    await wrapper.findComponent(UserPicker).vm.$emit("update:modelValue", 9)

    await add(wrapper)

    expect(mockAddSignUpAsBoard).not.toHaveBeenCalled()
  })

  it("asks no questions of an event without a sign-up form", () => {
    expect(dialog().find("[data-testid=answers-form]").exists()).toBe(false)
  })

  it("says the api's refusal and stays open", async () => {
    mockAddSignUpAsBoard.mockResolvedValue({ok: false, reason: "This person already has a sign-up for this event."})
    const wrapper = dialog()
    await wrapper.findComponent(UserPicker).vm.$emit("update:modelValue", 9)

    await add(wrapper)

    expect(wrapper.get("[data-testid=add-signup-failure]").text()).toBe("This person already has a sign-up for this event.")
    expect(wrapper.emitted("added")).toBeUndefined()
    expect(wrapper.emitted("update:modelValue")).toBeUndefined()
  })

  it("offers no guest on a members-only event, and tells the picker so", () => {
    const wrapper = dialog({membersOnly: true})

    expect(wrapper.find("[data-testid=add-signup-holder-guest]").exists()).toBe(false)
    expect(wrapper.findComponent(UserPicker).props("membersOnly")).toBe(true)
  })

  it("closes without adding on cancel", async () => {
    const wrapper = dialog()

    await wrapper.get("[data-testid=add-signup-cancel-btn]").trigger("click")

    expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
    expect(mockAddSignUpAsBoard).not.toHaveBeenCalled()
  })

  it("passes a close from the modal straight through", async () => {
    const wrapper = dialog()

    await wrapper.findComponent(ModalDialog).vm.$emit("update:open", false)

    expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
  })

  it("starts over when it opens again", async () => {
    mockAddSignUpAsBoard.mockResolvedValue({ok: false, reason: "Refused."})
    const wrapper = dialog()
    await asGuest(wrapper)
    await add(wrapper)
    await wrapper.setProps({modelValue: false})

    await wrapper.setProps({modelValue: true})

    expect(wrapper.get("[data-testid=add-signup-holder-account]").attributes("aria-checked")).toBe("true")
    expect(wrapper.find("[data-testid=add-signup-failure]").exists()).toBe(false)
  })
})
