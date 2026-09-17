import {describe, expect, it} from "vitest"
import {Role} from "@/services/api"
import {highestRole, highestRoleLabel} from "@/domains/user"

describe("highestRole", () => {
  it("reads the most senior role a person holds", () => {
    expect(highestRole([Role.GUEST, Role.MEMBER, Role.BOARD])).toBe(Role.BOARD)
  })

  it("does not depend on the order the api answered in", () => {
    expect(highestRole([Role.ADMIN, Role.MEMBER])).toBe(highestRole([Role.MEMBER, Role.ADMIN]))
  })

  it("ranks admin above treasurer above board", () => {
    expect(highestRole([Role.BOARD, Role.TREASURER])).toBe(Role.TREASURER)
    expect(highestRole([Role.TREASURER, Role.ADMIN])).toBe(Role.ADMIN)
  })

  it("ranks a role outside the inheritance chain below the members' one", () => {
    expect(highestRole([Role.MEMBER, Role.COMPANY])).toBe(Role.MEMBER)
  })

  it("has nothing to say about a person with no roles", () => {
    expect(highestRole([])).toBeNull()
    expect(highestRole(null)).toBeNull()
    expect(highestRole(undefined)).toBeNull()
  })

  it("names every role in the enum", () => {
    for (const role of Object.values(Role)) {
      expect(highestRole([role])).toBe(role)
    }
  })
})

describe("highestRoleLabel", () => {
  it("prints the role the column shows, lowercased", () => {
    expect(highestRoleLabel([Role.MEMBER, Role.ADMIN])).toBe("admin")
  })

  it("prints an empty column for a person with no roles", () => {
    expect(highestRoleLabel([])).toBe("")
  })
})
