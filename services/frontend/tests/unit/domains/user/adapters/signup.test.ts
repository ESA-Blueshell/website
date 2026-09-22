import {describe, expect, it, vi} from "vitest"
import {resumeSignupSession} from "@/domains/user/adapters/signup"
import {resumeSignup} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  resumeSignup: vi.fn(),
}))

describe("resumeSignupSession", () => {
  it("carries the token the applicant was mailed, in the header the api reads it from", async () => {
    vi.mocked(resumeSignup).mockResolvedValue({data: {userId: 3, email: "roos@esa.test"}} as never)

    await expect(resumeSignupSession("tok-7")).resolves.toEqual({userId: 3, email: "roos@esa.test"})
    expect(resumeSignup).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "tok-7"},
      throwOnError: true,
    })
  })

  it("answers with nothing when the api says nothing about the session", async () => {
    vi.mocked(resumeSignup).mockResolvedValue({} as never)

    await expect(resumeSignupSession("tok-7")).resolves.toBeNull()
  })

  // The page reads a rejection off the error itself, so the refusal has to reach it unchanged.
  it("throws the refusal rather than answering with no session", async () => {
    const refusal = new Error("rejected")
    vi.mocked(resumeSignup).mockRejectedValue(refusal)

    await expect(resumeSignupSession("tok-7")).rejects.toBe(refusal)
  })
})
