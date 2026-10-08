import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
import {nextTick} from "vue"

const {mockStore} = vi.hoisted(() => ({
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

import {
  useCountry,
  useReadonly,
  useSaving,
  useSubmitFeedback,
} from "@/composables/formUtils"

describe("formUtils composables", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isLoggedIn = true
    mockStore.getters.isBoard = false
  })

  it("tracks saving state around async action", async () => {
    const {isSaving, withSaving} = useSaving()
    expect(isSaving.value).toBe(false)

    const result = await withSaving(async () => "ok")
    expect(result).toBe("ok")
    expect(isSaving.value).toBe(false)
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
