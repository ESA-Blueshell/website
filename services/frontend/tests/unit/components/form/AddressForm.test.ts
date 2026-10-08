import {beforeEach, describe, expect, it, vi} from "vitest"
// aliased: the local mount helper below would otherwise shadow what it calls
import {mount as mountComponent} from "@vue/test-utils"
import AddressForm from "@/components/form/AddressForm.vue"
import {clearEveryField, saidByLabel} from "../../helpers/fields"

const {mockSaveNewAddress, mockSaveAddressChange, mockSaveSignupAddress, mockShowStatusMessage} = vi.hoisted(() => ({
  mockSaveNewAddress: vi.fn(),
  mockSaveAddressChange: vi.fn(),
  mockSaveSignupAddress: vi.fn(),
  mockShowStatusMessage: vi.fn(),
}))

vi.mock("@/plugins/handleNetworkError", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/plugins/handleNetworkError")>()
  return {...actual, $showStatusMessage: mockShowStatusMessage}
})

vi.mock("@/domains/user", () => ({
  saveNewAddress: mockSaveNewAddress,
  saveAddressChange: mockSaveAddressChange,
  saveSignupAddress: mockSaveSignupAddress,
}))

const full = {street: "Hallenweg", houseNumber: "5", zipCode: "7522NH", city: "Enschede", country: "NL"}

const fieldLabelled = (wrapper: ReturnType<typeof mountComponent>, label: string) =>
  wrapper.findAllComponents({name: "FormControl"}).find(field => field.props("label") === label)!

describe("AddressForm", () => {
  it("asks for every part of the address, and two characters of the street, zipcode and city", async () => {
    const wrapper = mountComponent(AddressForm, {props: {modelValue: {...full, street: "H", zipCode: "", city: "E"}, signupToken: "sel.ver"}})

    expect(await (wrapper.vm as any).save()).toBeNull()
    await wrapper.vm.$nextTick()

    expect(mockSaveSignupAddress).not.toHaveBeenCalled()
    expect(fieldLabelled(wrapper, "Street").props("errorMessages")).toEqual(["Must be at least 2 characters"])
    expect(fieldLabelled(wrapper, "Zipcode").props("errorMessages")).toEqual(["This field is required"])
    expect(fieldLabelled(wrapper, "City").props("errorMessages")).toEqual(["Must be at least 2 characters"])
    expect(fieldLabelled(wrapper, "House Number").props("errorMessages")).toEqual([])
  })

  it("takes what is typed into each field", async () => {
    const wrapper = mountComponent(AddressForm)
    const typed: Record<string, string> = {"Street": "Hallenweg", "House Number": "12a", "Zipcode": "7522NB", "City": "Enschede", "Country": "BE"}
    for (const [label, value] of Object.entries(typed)) fieldLabelled(wrapper, label).vm.$emit("update:modelValue", value)
    await wrapper.vm.$nextTick()

    expect((wrapper.vm as any).address).toMatchObject({street: "Hallenweg", houseNumber: "12a", zipCode: "7522NB", city: "Enschede", country: "BE"})
  })

  it("puts the api's refusal on the field it names", async () => {
    mockSaveSignupAddress.mockRejectedValue({response: {status: 400, data: {errors: [{field: "zipCode", message: "is not a Dutch zipcode"}]}}})
    const wrapper = mountComponent(AddressForm, {props: {modelValue: full, signupToken: "sel.ver"}, attrs: {"onUpdate:modelValue": vi.fn()}})

    await (wrapper.vm as any).save()
    await wrapper.vm.$nextTick()

    expect(fieldLabelled(wrapper, "Zipcode").props("errorMessages")).toEqual(["is not a Dutch zipcode"])
  })

  describe("saving", () => {
    beforeEach(() => {
      vi.clearAllMocks()
      mockSaveNewAddress.mockResolvedValue({id: 3, city: "Enschede"})
      mockSaveAddressChange.mockResolvedValue({id: 3, city: "Enschede"})
      mockSaveSignupAddress.mockResolvedValue(undefined)
    })

    const mount = (props: Record<string, unknown>) =>
      mountComponent(AddressForm, {
        props: {modelValue: full, ...props},
        attrs: {"onUpdate:modelValue": vi.fn()},
        global: {stubs: {SubmitButton: true}},
      })

    it("signup: saves on the token and never sends a userId", async () => {
      const wrapper = mount({signupToken: "sel.ver"})

      await (wrapper.vm as any).save()

      expect(mockSaveSignupAddress).toHaveBeenCalledTimes(1)
      const [token, body] = mockSaveSignupAddress.mock.calls[0]
      expect(token).toBe("sel.ver")
      expect(body).not.toHaveProperty("userId")
      expect(mockSaveNewAddress).not.toHaveBeenCalled()
    })

    it("signed in: creates the address through the session route", async () => {
      const wrapper = mount({userId: 7})

      await (wrapper.vm as any).save()

      expect(mockSaveNewAddress).toHaveBeenCalled()
      expect(mockSaveSignupAddress).not.toHaveBeenCalled()
    })

    it("updates the address it already has", async () => {
      const wrapper = mount({modelValue: {...full, id: 3, version: 1}})

      await (wrapper.vm as any).save()

      expect(mockSaveAddressChange).toHaveBeenCalled()
      expect(mockSaveNewAddress).not.toHaveBeenCalled()
    })

    it("hands back the address as saved, so a second save carries its new version", async () => {
      mockSaveAddressChange.mockResolvedValue({id: 3, city: "Enschede", version: 2})
      const wrapper = mount({modelValue: {...full, id: 3, version: 1}})

      expect(await (wrapper.vm as any).save()).toMatchObject({id: 3, version: 2})
    })

    it("says so rather than posting an address at nobody", async () => {
      const wrapper = mount({})

      expect(await (wrapper.vm as any).save()).toBeNull()

      expect(mockSaveNewAddress).not.toHaveBeenCalled()
      expect(mockSaveSignupAddress).not.toHaveBeenCalled()
      expect(mockShowStatusMessage).toHaveBeenCalled()
      expect(wrapper.emitted("submitted")).toEqual([[false]])
    })

    it("signup: a refused save surfaces as a failed submit", async () => {
      mockSaveSignupAddress.mockRejectedValue(new Error("expired"))
      const wrapper = mount({signupToken: "sel.ver"})

      expect(await (wrapper.vm as any).save()).toBeNull()
      expect(wrapper.emitted("submitted")).toEqual([[false]])
    })
  })

  it("says a field left empty is required once it is left", async () => {
    const wrapper = mountComponent(AddressForm, {props: {modelValue: {...full, country: ""}}})

    await clearEveryField(wrapper)

    const required = ["This field is required"]
    expect(saidByLabel(wrapper)).toEqual({"Street": required, "House Number": required, "Zipcode": required, "City": required, "Country": required})
  })
})
