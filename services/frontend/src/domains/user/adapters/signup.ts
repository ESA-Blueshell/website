/**
 * Signup adapter: the reads a signup in progress makes. One of the few files in this domain
 * that reaches the generated client (frontend ADR-001); everything else comes through the door.
 */
import {resumeSignup, type SignupResumeResponse} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"

/**
 * The signup behind the token an applicant was mailed, or nothing where the api would not say.
 *
 * The refusal is thrown as it came, because a retired token is told apart from any other
 * rejection by reading the error itself.
 */
export async function resumeSignupSession(token: string): Promise<SignupResumeResponse | null> {
  const res = await resumeSignup({headers: {[SIGNUP_TOKEN_HEADER]: token}, throwOnError: true})
  return res.data ?? null
}
