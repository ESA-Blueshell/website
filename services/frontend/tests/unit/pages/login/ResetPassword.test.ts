import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import ResetPassword from "@/pages/login/ResetPassword.vue"
import {mountInApp, settle} from "../helpers"
import {clearEveryField, saidByLabel} from "../../helpers/fields"

const {
  mockRoute,
  mockRouterReplace,
  mockSetNewPassword,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockRoute: {
    query: {},
    hash: "#token=reset-token",
  },
  mockRouterReplace: vi.fn(),
  mockSetNewPassword: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
    useRouter: () => ({
      replace: mockRouterReplace,
    }),
  }
})

vi.mock("@/domains/recovery", () => ({
  setNewPassword: mockSetNewPassword,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("ResetPassword page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockRoute.query = {}
    mockRoute.hash = "#token=reset-token"
    mockSetNewPassword.mockResolvedValue(undefined)
  })

  const fields = (wrapper: ReturnType<typeof mountInApp>) => wrapper.findAllComponents({name: "FormControl"})
  const typeBoth = async (wrapper: ReturnType<typeof mountInApp>, password: string, again: string) => {
    fields(wrapper)[0]!.vm.$emit("update:modelValue", password)
    fields(wrapper)[1]!.vm.$emit("update:modelValue", again)
    await settle()
  }
  const send = async (wrapper: ReturnType<typeof mountInApp>) => {
    await wrapper.get('[data-testid="reset-password-form"]').trigger("submit")
    await settle()
  }

  it("reads token from hash, strips it from URL, and submits reset request", async () => {
    const wrapper = mountInApp(ResetPassword)
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith({
      query: {},
      hash: "",
    })

    await typeBoth(wrapper, "NewPass123!", "NewPass123!")
    await send(wrapper)

    expect(mockSetNewPassword).toHaveBeenCalledWith({
      password: "NewPass123!",
      token: "reset-token",
    })
    expect(wrapper.find('[data-testid="reset-password-success-state"]').exists()).toBe(true)
  })

  it("sends nothing while the password is weak or its repeat differs, and says which", async () => {
    const wrapper = mountInApp(ResetPassword)
    await settle()

    await typeBoth(wrapper, "newpass123!", "other")
    await send(wrapper)

    expect(mockSetNewPassword).not.toHaveBeenCalled()
    expect(fields(wrapper)[0]!.props("errorMessages")).toEqual(["Include an uppercase letter"])
    expect(fields(wrapper)[1]!.props("errorMessages")).toEqual(["Values do not match"])
    expect(fields(wrapper)[0]!.props("kind")).toBe("password")
  })

  it("says the link may be spent when the api refuses it without naming a field", async () => {
    mockSetNewPassword.mockRejectedValue(new Error("gone"))
    const wrapper = mountInApp(ResetPassword)
    await settle()

    await typeBoth(wrapper, "NewPass123!", "NewPass123!")
    await send(wrapper)

    expect(wrapper.get('[data-testid="reset-password-error-alert"]').text()).toContain("may be invalid or expired")
  })

  it("redirects home when token is absent", async () => {
    mockRoute.query = {}
    mockRoute.hash = ""

    shallowMount(ResetPassword)
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith({name: "home"})
  })

  it("says Reset Password on its button", async () => {
    const wrapper = mountInApp(ResetPassword)
    await settle()

    expect(wrapper.get('[data-testid="reset-password-submit-btn"]').text()).toBe("Reset Password")
  })

  it("says a field left empty is required once it is left", async () => {
    const wrapper = mountInApp(ResetPassword)
    await settle()

    await clearEveryField(wrapper)

    expect(saidByLabel(wrapper)).toMatchObject({"New Password": ["This field is required"], "Repeat New Password": ["This field is required"]})
  })
})
