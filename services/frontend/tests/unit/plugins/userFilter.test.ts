import {describe, expect, it} from "vitest"
import {filterUsers, matchUser} from "@/plugins/userFilter"

type User = {
  fullName: string
  username: string
  role?: string
}

const users: User[] = [
  {fullName: "Emma Dokter", username: "lyndisluna", role: "BOARD"},
  {fullName: "Viktor Petrov", username: "ariosfury", role: "MEMBER"},
]

describe("userFilter plugin", () => {
  it("matches across multiple terms", () => {
    expect(matchUser(users[0], "emma lyn")).toBe(true)
    expect(matchUser(users[0], "emma missing")).toBe(false)
  })

  it("supports case-sensitive matching", () => {
    expect(matchUser(users[0], "Emma", {caseSensitive: true})).toBe(true)
    expect(matchUser(users[0], "emma", {caseSensitive: true})).toBe(false)
  })

  it("filters by keys", () => {
    const result = filterUsers(users, "board", {keys: ["role"]})
    expect(result).toEqual([users[0]])
  })

  it("returns defensive defaults for invalid input", () => {
    expect(filterUsers([] as User[], "")).toEqual([])
    expect(filterUsers(users, "")).toEqual(users)
  })
})
