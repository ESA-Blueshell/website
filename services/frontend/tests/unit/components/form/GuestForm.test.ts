import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import GuestForm from "@/components/form/GuestForm.vue"

const {mockStore} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      isLoggedIn: false,
    },
  },
}))

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...actual,
    useStore: () => mockStore,
  }
})
vi.mock("flag-icons/css/flag-icons.min.css", () => ({}))
vi.mock("v-phone-input/styles", () => ({}))

vi.mock("v-phone-input", () => ({}))

const capturedProps: Record<string, unknown>[] = []
const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules", "component", "componentProps"],
  setup(props: Record<string, unknown>) {
    capturedProps.push({...props})
  },
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' />",
}
const formStub = {template: "<div><slot /></div>"}

function rulesByName(wrapper: ReturnType<typeof shallowMount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("GuestForm", () => {
  beforeEach(() => {
    mockStore.getters.isLoggedIn = false
    capturedProps.length = 0
  })

  it("declares all guest validation rules", () => {
    const wrapper = shallowMount(GuestForm, {
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    expect(rulesByName(wrapper)).toMatchObject({
      name: "required",
      discord: "required",
      email: "required|email|noStudentEmail",
      phoneNumber: "required|phoneMobile:NL",
    })
  })

  it("uses the globally registered VPhoneInput component for the phone field", () => {
    shallowMount(GuestForm, {
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    const phoneField = capturedProps.find((p) => p.name === "phoneNumber")
    expect(phoneField).toBeDefined()
    expect(phoneField!.component).toBe("VPhoneInput")
  })

  it("hides guest form fields for logged-in users", () => {
    mockStore.getters.isLoggedIn = true
    const wrapper = shallowMount(GuestForm, {
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    expect(wrapper.findAll(".vv-field-stub")).toHaveLength(0)
  })
})
