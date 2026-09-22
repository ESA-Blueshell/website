import {describe, expect, it, vi} from "vitest"
import {correctSignupEmail} from "@/domains/recovery/adapters/recovery"
import {correctEmail} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  correctEmail: vi.fn(),
}))

describe("correctSignupEmail", () => {
  it("carries the token the applicant holds, in the header the api reads it from", async () => {
    vi.mocked(correctEmail).mockResolvedValue({} as never)

    await correctSignupEmail("sel.ver", "corrected@example.com")

    expect(correctEmail).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "sel.ver"},
      body: {email: "corrected@example.com"},
      throwOnError: true,
    })
  })

  it("throws the refusal the form reads its fields from", async () => {
    vi.mocked(correctEmail).mockRejectedValue(new Error("taken"))

    await expect(correctSignupEmail("sel.ver", "taken@example.com")).rejects.toBeDefined()
  })
})
