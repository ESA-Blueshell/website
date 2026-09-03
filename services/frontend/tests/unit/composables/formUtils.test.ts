import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
import {nextTick} from "vue"

const {mockApply, mockHandleNetworkError, mockShowStatusMessage, mockStore} = vi.hoisted(() => ({
  mockApply: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockShowStatusMessage: vi.fn(),
  mockStore: {
    getters: {
      isLoggedIn: true,
      isBoard: false,
    },
  },
}))

vi.mock("vuex", () => ({
  useStore: () => mockStore,
}))

vi.mock("@/plugins/validation.ts", () => ({
  apply: mockApply,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
  $showStatusMessage: mockShowStatusMessage,
}))

import {
  handleSubmitError,
  useCountry,
  usePasswordToggle,
  useReadonly,
  useSaving,
  useSubmitFeedback,
  useVeeForm,
} from "@/composables/formUtils"

describe("formUtils composables", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isLoggedIn = true
    mockStore.getters.isBoard = false
  })

  it("validates vee form through formRef", async () => {
    const {formRef, validate} = useVeeForm()
    formRef.value = {
      validate: vi.fn().mockResolvedValue({valid: true}),
    } as never

    await expect(validate()).resolves.toBe(true)
  })

  it("tracks saving state around async action", async () => {
    const {isSaving, withSaving} = useSaving()
    expect(isSaving.value).toBe(false)

    const result = await withSaving(async () => "ok")
    expect(result).toBe("ok")
    expect(isSaving.value).toBe(false)
  })

  it("delegates submit errors to field validation first", () => {
    mockApply.mockReturnValue({messages: []})
    handleSubmitError({} as never, new Error("x"))
    expect(mockApply).toHaveBeenCalled()
    expect(mockHandleNetworkError).not.toHaveBeenCalled()
    expect(mockShowStatusMessage).not.toHaveBeenCalled()
  })

  it("falls back to network error handler when the error is not a field validation one", () => {
    mockApply.mockReturnValue(null)
    const error = new Error("x")
    handleSubmitError({} as never, error)
    expect(mockHandleNetworkError).toHaveBeenCalledWith(error)
  })

  it("says out loud what it could not attach to a field", () => {
    mockApply.mockReturnValue({messages: ["userId: must be greater than 0"]})
    handleSubmitError({} as never, new Error("x"))
    expect(mockHandleNetworkError).not.toHaveBeenCalled()
    expect(mockShowStatusMessage).toHaveBeenCalledWith(
      "This form is not accepted: userId: must be greater than 0"
    )
  })

  it("derives readonly state from auth/board getters", async () => {
    const {isReadonly} = useReadonly()
    await nextTick()
    expect(isReadonly.value).toBe(true)

    mockStore.getters.isBoard = true
    const boardState = useReadonly()
    await nextTick()
    expect(boardState.isReadonly.value).toBe(false)
  })

  it("updates country value", () => {
    const {country, onCountryUpdate} = useCountry("NL")
    onCountryUpdate("DE")
    expect(country.value).toBe("DE")
  })

  it("toggles password field props", () => {
    const {isPasswordVisible, passwordFieldProps} = usePasswordToggle(false)
    expect(passwordFieldProps.value.type).toBe("password")
    passwordFieldProps.value["onClick:append-inner"]()
    expect(isPasswordVisible.value).toBe(true)
    expect(passwordFieldProps.value.type).toBe("text")
  })

  it("shows transient submit feedback state", () => {
    vi.useFakeTimers()
    const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback(100)

    setSubmitResult(true)
    expect(submitState.value).toBe("success")
    expect(showSubmitStatus.value).toBe(true)

    vi.advanceTimersByTime(100)
    expect(submitState.value).toBe("idle")
    expect(showSubmitStatus.value).toBe(false)
  })

  afterEach(() => {
    vi.useRealTimers()
  })
})
