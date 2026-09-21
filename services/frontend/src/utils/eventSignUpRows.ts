import type {AnswerResponse, EventSignUpResponse} from "@/services/api"

/** The contact details a sign-up carries, from whichever of its two sides holds them. */
export type SignUpPerson = {
  name: string;
  discord: string;
  email: string;
  phoneNumber: string;
}

/**
 * A sign-up as the sign-ups page reads it: the sign-up itself, plus its answers keyed by question.
 *
 * The sign-up stays whole because the row-level actions need its id and version, and the
 * guest-or-account distinction, none of which survive being flattened into contact details.
 */
export type SignUpRow = {
  signUp: EventSignUpResponse;
  answers: Map<number, AnswerResponse>;
}

/**
 * A sign-up belongs either to an account or to a guest, so one side answers for all four details.
 * A user is known by `fullName` and a guest by `name`.
 */
export function signUpPerson(signUp: EventSignUpResponse): SignUpPerson {
  const source = signUp.user ?? signUp.guest
  return {
    name: signUp.user?.fullName ?? signUp.guest?.name ?? "",
    discord: source?.discord ?? "",
    email: source?.email ?? "",
    phoneNumber: source?.phoneNumber ?? "",
  }
}

export function toSignUpRows(signUps: EventSignUpResponse[]): SignUpRow[] {
  return signUps.map((signUp) => ({
    signUp,
    answers: new Map((signUp.answers ?? []).map((answer) => [answer.questionId, answer])),
  }))
}
