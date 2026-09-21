import {describe, expect, it} from "vitest"
import {type EventSignUpResponse, EventSignUpKind} from "@/services/api"
import {signUpKindLabel, signUpPerson, sortRowsByKind, toSignUpRows} from "@/utils/eventSignUpRows"

function accountSignUp(overrides: Partial<EventSignUpResponse> = {}): EventSignUpResponse {
  return {
    id: 1,
    eventId: 55,
    version: 0,
    createdAt: "2026-01-01T10:00:00Z",
    updatedAt: "2026-01-01T10:00:00Z",
    answers: [],
    kind: EventSignUpKind.MEMBER,
    user: {
      id: 9,
      version: 0,
      createdAt: "2025-01-01T10:00:00Z",
      updatedAt: "2025-01-01T10:00:00Z",
      fullName: "Alice Ash",
      email: "alice@example.com",
      discord: "alice#1234",
      phoneNumber: "123",
    },
    ...overrides,
  } as EventSignUpResponse
}

function guestSignUp(overrides: Partial<EventSignUpResponse> = {}): EventSignUpResponse {
  return {
    id: 2,
    eventId: 55,
    version: 0,
    createdAt: "2026-01-02T10:00:00Z",
    updatedAt: "2026-01-02T10:00:00Z",
    answers: [],
    kind: EventSignUpKind.GUEST,
    guest: {
      id: 4,
      version: 0,
      createdAt: "2026-01-02T10:00:00Z",
      updatedAt: "2026-01-02T10:00:00Z",
      name: "Bob Birch",
      email: "bob@example.com",
      discord: "bob#5555",
      phoneNumber: "456",
    },
    ...overrides,
  } as EventSignUpResponse
}

describe("signUpPerson", () => {
  it("reads an account signup from the user", () => {
    expect(signUpPerson(accountSignUp())).toEqual({
      name: "Alice Ash",
      discord: "alice#1234",
      email: "alice@example.com",
      phoneNumber: "123",
    })
  })

  it("reads a guest signup from the guest", () => {
    expect(signUpPerson(guestSignUp())).toEqual({
      name: "Bob Birch",
      discord: "bob#5555",
      email: "bob@example.com",
      phoneNumber: "456",
    })
  })

  it("answers empty strings when neither side carries a detail", () => {
    const bare = {...guestSignUp(), guest: undefined} as EventSignUpResponse
    expect(signUpPerson(bare)).toEqual({name: "", discord: "", email: "", phoneNumber: ""})
  })
})

describe("toSignUpRows", () => {
  it("keeps the signup itself on every row", () => {
    const signUps = [accountSignUp(), guestSignUp()]
    const rows = toSignUpRows(signUps)

    expect(rows.map((row) => row.signUp.id)).toEqual([1, 2])
    expect(rows[0]!.signUp).toBe(signUps[0])
  })

  it("indexes the answers by question", () => {
    const rows = toSignUpRows([
      accountSignUp({
        answers: [
          {questionId: 2, optionSelections: [true, false]},
          {questionId: 3, textResponse: "No peanuts"},
        ],
      } as Partial<EventSignUpResponse>),
    ])

    expect(rows[0]!.answers.get(3)?.textResponse).toBe("No peanuts")
    expect(rows[0]!.answers.get(2)?.optionSelections).toEqual([true, false])
    expect(rows[0]!.answers.has(99)).toBe(false)
  })

  it("survives a signup with no answers at all", () => {
    const rows = toSignUpRows([{...accountSignUp(), answers: undefined} as EventSignUpResponse])
    expect(rows[0]!.answers.size).toBe(0)
  })
})

describe("signUpKindLabel", () => {
  it("names each kind the way the roster reads", () => {
    expect(signUpKindLabel(EventSignUpKind.GUEST)).toBe("Guest")
    expect(signUpKindLabel(EventSignUpKind.NON_MEMBER)).toBe("Non-member")
    expect(signUpKindLabel(EventSignUpKind.MEMBER)).toBe("Member")
  })
})

describe("sortRowsByKind", () => {
  const rows = () =>
    toSignUpRows([
      accountSignUp({id: 1, kind: EventSignUpKind.MEMBER} as Partial<EventSignUpResponse>),
      guestSignUp({id: 2, kind: EventSignUpKind.GUEST} as Partial<EventSignUpResponse>),
      accountSignUp({id: 3, kind: EventSignUpKind.NON_MEMBER} as Partial<EventSignUpResponse>),
      guestSignUp({id: 4, kind: EventSignUpKind.GUEST} as Partial<EventSignUpResponse>),
    ])

  it("leaves the rows in signup order when no direction is asked for", () => {
    expect(sortRowsByKind(rows(), null).map((row) => row.signUp.id)).toEqual([1, 2, 3, 4])
  })

  it("groups guests first ascending", () => {
    expect(sortRowsByKind(rows(), "asc").map((row) => row.signUp.id)).toEqual([2, 4, 3, 1])
  })

  it("groups members first descending", () => {
    expect(sortRowsByKind(rows(), "desc").map((row) => row.signUp.id)).toEqual([1, 3, 2, 4])
  })

  it("keeps signup order within a kind, and leaves the given rows alone", () => {
    const given = rows()
    const sorted = sortRowsByKind(given, "asc")

    expect(sorted.slice(0, 2).map((row) => row.signUp.id)).toEqual([2, 4])
    expect(given.map((row) => row.signUp.id)).toEqual([1, 2, 3, 4])
  })
})
