import {describe, expect, it, vi} from "vitest"
import {resumeSignupSession} from "@/domains/user/adapters/signup"
import {resumeSignup} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import {answer, emptyAnswer} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  resumeSignup: vi.fn(),
}))

describe("resumeSignupSession", () => {
  it("carries the token the applicant was mailed, in the header the api reads it from", async () => {
    const session = {
      userId: 3,
      username: "roos",
      email: "roos@esa.test",
      firstName: "Roos",
      initials: "R.",
      lastName: "Visser",
      emailConfirmed: false,
      conditionsAccepted: false,
      newsletter: false,
      photoConsent: false,
    }
    vi.mocked(resumeSignup).mockResolvedValue(answer(resumeSignup, session))

    await expect(resumeSignupSession("tok-7")).resolves.toEqual(session)
    expect(resumeSignup).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "tok-7"},
      throwOnError: true,
    })
  })

  it("answers with nothing when the api says nothing about the session", async () => {
    vi.mocked(resumeSignup).mockResolvedValue(emptyAnswer(resumeSignup))

    await expect(resumeSignupSession("tok-7")).resolves.toBeNull()
  })

  // The page reads a rejection off the error itself, so the refusal has to reach it unchanged.
  it("throws the refusal rather than answering with no session", async () => {
    const refusal = new Error("rejected")
    vi.mocked(resumeSignup).mockRejectedValue(refusal)

    await expect(resumeSignupSession("tok-7")).rejects.toBe(refusal)
  })
})
