import {describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import EditSignUpDialog from "@/components/common/modals/EditSignUpDialog.vue"

vi.mock("@/components/form/EventSignUpForm.vue", () => ({
  default: {
    name: "EventSignUpForm",
    // Typed, so the bare `board-edit` attribute casts to true the way it does on the real form.
    props: {event: Object, initialSignUp: Object, boardEdit: Boolean},
    template: "<div />",
  },
}))

const event = {id: 500, title: "LAN"} as never
const signUp = {id: 44, version: 3, answers: []} as never

// The island's dialog portals to the body; a stand-in keeps the form where it can be read.
const modalStub = {
  name: "ModalDialog",
  props: ["open", "title", "testid"],
  emits: ["update:open"],
  template: "<div><slot /></div>",
}

function dialog(modelValue = true) {
  return shallowMount(EditSignUpDialog, {
    props: {modelValue, event, signUp},
    global: {stubs: {ModalDialog: modalStub}},
  })
}

describe("EditSignUpDialog", () => {
  it("hands the form the sign-up it is editing, in board mode", () => {
    const form = dialog().findComponent({name: "EventSignUpForm"})

    expect(form.props("boardEdit")).toBe(true)
    expect(form.props("initialSignUp")).toEqual(signUp)
    expect(form.props("event")).toEqual(event)
  })

  it("reports a save and closes itself", async () => {
    const wrapper = dialog()
    const saved = {id: 44, version: 4, answers: []}

    await wrapper.findComponent({name: "EventSignUpForm"}).vm.$emit("update:signUp", saved)

    expect(wrapper.emitted("saved")).toEqual([[saved]])
    expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
  })

  it("passes a close from the modal straight through", async () => {
    const wrapper = dialog()

    await wrapper.findComponent({name: "ModalDialog"}).vm.$emit("update:open", false)

    expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
    expect(wrapper.emitted("saved")).toBeUndefined()
  })
})
