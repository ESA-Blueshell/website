import {type AnswerResponse, EventSignUpKind, type EventSignUpResponse} from "@/services/api"

export type SignUpPerson = {
  name: string;
  discord: string;
  email: string;
  phoneNumber: string;
}

/**
 * A sign-up as the tables read it, kept whole: the row actions need its id, version and holder,
 * none of which survive being flattened into contact details.
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

const KIND_ORDER: Record<EventSignUpKind, number> = {
  [EventSignUpKind.GUEST]: 0,
  [EventSignUpKind.NON_MEMBER]: 1,
  [EventSignUpKind.MEMBER]: 2,
}

const KIND_LABELS: Record<EventSignUpKind, string> = {
  [EventSignUpKind.GUEST]: "Guest",
  [EventSignUpKind.NON_MEMBER]: "Non-member",
  [EventSignUpKind.MEMBER]: "Member",
}

export type KindSort = "asc" | "desc" | null

export function signUpKindLabel(kind: EventSignUpKind): string {
  return KIND_LABELS[kind]
}

/** Sorts a copy, so the rows keep their signup order for the reader who asked for none. */
export function sortRowsByKind(rows: SignUpRow[], direction: KindSort): SignUpRow[] {
  if (!direction) return rows
  const sign = direction === "asc" ? 1 : -1
  return [...rows].sort(
    (a, b) => sign * (KIND_ORDER[a.signUp.kind] - KIND_ORDER[b.signUp.kind]),
  )
}

/** A board edit rewrites guest details or form answers; an account sign-up without a form has neither. */
export function isSignUpEditable(signUp: EventSignUpResponse, eventHasForm: boolean): boolean {
  return eventHasForm || signUp.guest != null
}
