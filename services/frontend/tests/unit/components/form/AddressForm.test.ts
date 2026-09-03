import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import AddressForm from "@/components/form/AddressForm.vue"

const {mockCreateAddress, mockUpdateAddress, mockSaveAddress, mockShowStatusMessage} = vi.hoisted(() => ({
  mockCreateAddress: vi.fn(),
  mockUpdateAddress: vi.fn(),
  mockSaveAddress: vi.fn(),
  mockShowStatusMessage: vi.fn(),
}))

vi.mock("@/plugins/handleNetworkError", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/plugins/handleNetworkError")>()
  return {...actual, $showStatusMessage: mockShowStatusMessage}
})

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {
    ...actual,
    createAddress: mockCreateAddress,
    updateAddress: mockUpdateAddress,
    saveAddress: mockSaveAddress,
  }
})

vi.mock("@/composables/formUtils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/composables/formUtils")>()
  return {
    ...actual,
    useVeeForm: () => ({formRef: {value: {}}, validate: () => Promise.resolve(true)}),
  }
})

const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules"],
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' />",
}
const formStub = {template: "<div><slot /></div>"}

function fieldRules(wrapper: ReturnType<typeof shallowMount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("AddressForm", () => {
  it("declares validation rules for all address fields", () => {
    const wrapper = shallowMount(AddressForm, {
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    expect(fieldRules(wrapper)).toMatchObject({
      street: "required|minChars:2",
      houseNumber: "required",
      zipCode: "required|minChars:2",
      city: "required|minChars:2",
      country: "required",
    })
  })


  describe("saving", () => {
    beforeEach(() => {
      vi.clearAllMocks()
      mockCreateAddress.mockResolvedValue({data: {id: 3, city: "Enschede"}})
      mockUpdateAddress.mockResolvedValue({data: {id: 3, city: "Enschede"}})
      mockSaveAddress.mockResolvedValue({data: undefined})
    })

    const mount = (props: Record<string, unknown>) =>
      shallowMount(AddressForm, {
        props,
        attrs: {"onUpdate:modelValue": vi.fn()},
        global: {stubs: {Form: formStub, VvField: vvFieldStub, SubmitButton: true, CountrySelect: true}},
      })

    it("signup: saves on the token and never sends a userId", async () => {
      const wrapper = mount({signupToken: "sel.ver"})

      await (wrapper.vm as any).save()

      expect(mockSaveAddress).toHaveBeenCalledTimes(1)
      const call = mockSaveAddress.mock.calls[0][0]
      expect(call.headers).toEqual({"X-Signup-Token": "sel.ver"})
      expect(call.body).not.toHaveProperty("userId")
      expect(mockCreateAddress).not.toHaveBeenCalled()
    })

    it("signed in: creates the address through the session route", async () => {
      const wrapper = mount({userId: 7})

      await (wrapper.vm as any).save()

      expect(mockCreateAddress).toHaveBeenCalled()
      expect(mockSaveAddress).not.toHaveBeenCalled()
    })

    it("updates the address it already has", async () => {
      const wrapper = mount({modelValue: {id: 3, city: "Enschede", version: 1}})

      await (wrapper.vm as any).save()

      expect(mockUpdateAddress).toHaveBeenCalled()
      expect(mockCreateAddress).not.toHaveBeenCalled()
    })

    it("says so rather than posting an address at nobody", async () => {
      const wrapper = mount({})

      expect(await (wrapper.vm as any).save()).toBeNull()

      expect(mockCreateAddress).not.toHaveBeenCalled()
      expect(mockSaveAddress).not.toHaveBeenCalled()
      expect(mockShowStatusMessage).toHaveBeenCalled()
      expect(wrapper.emitted("submitted")).toEqual([[false]])
    })

    it("signup: a refused save surfaces as a failed submit", async () => {
      mockSaveAddress.mockRejectedValue(new Error("expired"))
      const wrapper = mount({signupToken: "sel.ver"})

      expect(await (wrapper.vm as any).save()).toBeNull()
      expect(wrapper.emitted("submitted")).toEqual([[false]])
    })
  })
})
