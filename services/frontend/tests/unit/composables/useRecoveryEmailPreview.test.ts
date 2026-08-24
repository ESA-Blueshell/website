import {beforeEach, describe, expect, it, vi} from "vitest"

const {mockPreviewRecoveryEmail} = vi.hoisted(() => ({mockPreviewRecoveryEmail: vi.fn()}))

vi.mock("@/services/api", () => ({
  previewRecoveryEmail: mockPreviewRecoveryEmail,
  TokenPurpose: {
    USER_ACTIVATION: "USER_ACTIVATION",
    MEMBER_ACTIVATION: "MEMBER_ACTIVATION",
    PASSWORD_RESET: "PASSWORD_RESET",
    SIGNUP_CONTINUATION: "SIGNUP_CONTINUATION",
  },
}))

const {useRecoveryEmailPreview} = await import("@/composables/useRecoveryEmailPreview")
const {TokenPurpose} = await import("@/services/api")

const rendered = {
  purpose: "USER_ACTIVATION",
  subject: "Activate your Account",
  html: "<p>hello</p>",
  recipientEmail: "alice@example.com",
  recipientName: "Alice Regular",
  linkPlaceholder: "PREVIEW-ONLY-NO-TOKEN-ISSUED",
}

describe("useRecoveryEmailPreview", () => {
  beforeEach(() => vi.clearAllMocks())

  it("asks for the requested user and purpose", async () => {
    mockPreviewRecoveryEmail.mockResolvedValue({data: rendered})
    const {show, preview, open, loading} = useRecoveryEmailPreview()

    await show(42, TokenPurpose.USER_ACTIVATION)

    expect(mockPreviewRecoveryEmail).toHaveBeenCalledWith({
      path: {userId: 42},
      query: {purpose: "USER_ACTIVATION"},
    })
    expect(open.value).toBe(true)
    expect(loading.value).toBe(false)
    expect(preview.value).toEqual(rendered)
  })

  it("opens straight away so the dialog can show progress", () => {
    let settle: (v: unknown) => void = () => {}
    mockPreviewRecoveryEmail.mockReturnValue(new Promise((r) => (settle = r)))
    const {show, open, loading} = useRecoveryEmailPreview()

    void show(42, TokenPurpose.PASSWORD_RESET)

    expect(open.value).toBe(true)
    expect(loading.value).toBe(true)
    settle({data: rendered})
  })

  it("reports a failure rather than showing an empty email", async () => {
    mockPreviewRecoveryEmail.mockResolvedValue({error: {status: 403}})
    const {show, error, preview} = useRecoveryEmailPreview()

    await show(42, TokenPurpose.USER_ACTIVATION)

    expect(error.value).toBe("The preview could not be rendered.")
    expect(preview.value).toBeNull()
  })

  it("clears the previous email before fetching the next", async () => {
    mockPreviewRecoveryEmail.mockResolvedValue({data: rendered})
    const {show, preview, error} = useRecoveryEmailPreview()
    await show(42, TokenPurpose.USER_ACTIVATION)

    mockPreviewRecoveryEmail.mockResolvedValue({error: {status: 500}})
    await show(43, TokenPurpose.PASSWORD_RESET)

    // A stale email must not sit under a fresh error.
    expect(preview.value).toBeNull()
    expect(error.value).not.toBeNull()
  })
})
