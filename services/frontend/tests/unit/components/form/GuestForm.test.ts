import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import GuestForm from "@/components/form/GuestForm.vue"
import {clearEveryField, saidByLabel} from "../../helpers/fields"

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

const empty = () => ({name: "", discord: "", email: "", phoneNumber: ""})

const fieldLabelled = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAllComponents({name: "FormControl"}).find(field => field.props("label") === label)!

describe("GuestForm", () => {
  beforeEach(() => {
    mockStore.getters.isLoggedIn = false
  })

  it("asks for every detail, a real e-mail address and a mobile number", async () => {
    const wrapper = mount(GuestForm, {props: {modelValue: {...empty(), email: "gordon", phoneNumber: "+31201234567"}}})

    expect(await (wrapper.vm as any).validate()).toBe(false)
    await wrapper.vm.$nextTick()

    expect(fieldLabelled(wrapper, "Full name*").props("errorMessages")).toEqual(["This field is required"])
    expect(fieldLabelled(wrapper, "Discord username*").props("errorMessages")).toEqual(["This field is required"])
    expect(fieldLabelled(wrapper, "Email*").props("errorMessages")).toEqual(["Enter a valid e-mail address"])
    expect(fieldLabelled(wrapper, "Phone Number*").props("errorMessages")).toEqual(["Enter a mobile phone number"])
  })

  it("lets complete details through", async () => {
    const wrapper = mount(GuestForm, {
      props: {modelValue: {name: "Gordon", discord: "gordon", email: "gordon@example.com", phoneNumber: "+31612345678"}},
    })

    expect(await (wrapper.vm as any).validate()).toBe(true)
  })

  it("asks the island control for a phone field", () => {
    const wrapper = mount(GuestForm)

    expect(fieldLabelled(wrapper, "Phone Number*").props("kind")).toBe("phone")
  })

  it("hides guest form fields for logged-in users", () => {
    mockStore.getters.isLoggedIn = true
    const wrapper = mount(GuestForm)

    expect(wrapper.findAllComponents({name: "FormControl"})).toHaveLength(0)
  })

  it("writes what each field reports back onto the guest", async () => {
    const guest = empty()
    const wrapper = mount(GuestForm, {
      props: {modelValue: guest, "onUpdate:modelValue": (v: typeof guest) => Object.assign(guest, v)},
    })

    fieldLabelled(wrapper, "Full name*").vm.$emit("update:modelValue", "Guest Gordon")
    fieldLabelled(wrapper, "Discord username*").vm.$emit("update:modelValue", "gordon#0001")
    fieldLabelled(wrapper, "Email*").vm.$emit("update:modelValue", "gordon@example.com")
    fieldLabelled(wrapper, "Phone Number*").vm.$emit("update:modelValue", "+31612345678")
    await wrapper.vm.$nextTick()

    expect(guest).toEqual({
      name: "Guest Gordon",
      discord: "gordon#0001",
      email: "gordon@example.com",
      phoneNumber: "+31612345678",
    })
  })

  it("shows the fields to a logged-in board member editing somebody else, without the sign-in notice", () => {
    mockStore.getters.isLoggedIn = true

    const wrapper = mount(GuestForm, {props: {force: true}})

    expect(wrapper.findAllComponents({name: "FormControl"})).toHaveLength(4)
    expect(wrapper.find("[data-testid='guest-form-signed-out']").exists()).toBe(false)
  })

  it("says a field left empty is required once it is left", async () => {
    const wrapper = mount(GuestForm)

    await clearEveryField(wrapper)

    expect(saidByLabel(wrapper)).toEqual({"Full name*": ["This field is required"], "Discord username*": ["This field is required"], "Email*": ["This field is required"], "Phone Number*": ["This field is required"]})
  })

  it("checks nothing it does not show", async () => {
    mockStore.getters.isLoggedIn = true
    const wrapper = mount(GuestForm)

    expect(await (wrapper.vm as any).validate()).toBe(true)
  })
})
