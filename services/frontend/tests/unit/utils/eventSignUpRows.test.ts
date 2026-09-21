import {describe, expect, it} from "vitest"
import type {EventSignUpResponse} from "@/services/api"
import {signUpPerson, toSignUpRows} from "@/utils/eventSignUpRows"

function accountSignUp(overrides: Partial<EventSignUpResponse> = {}): EventSignUpResponse {
  return {
    id: 1,
    eventId: 55,
    version: 0,
    createdAt: "2026-01-01T10:00:00Z",
    updatedAt: "2026-01-01T10:00:00Z",
    answers: [],
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
