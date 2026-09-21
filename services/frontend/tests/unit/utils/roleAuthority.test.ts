import {describe, expect, it} from "vitest"
import {Role} from "@/services/api"
import {hasAuthority} from "@/utils/roleAuthority"

describe("hasAuthority", () => {
  it("answers true for the role itself", () => {
    expect(hasAuthority([Role.BOARD], Role.BOARD)).toBe(true)
  })

  it("answers true for a role reached through the chain", () => {
    expect(hasAuthority([Role.ADMIN], Role.BOARD)).toBe(true)
    expect(hasAuthority([Role.TREASURER], Role.BOARD)).toBe(true)
    expect(hasAuthority([Role.BOARD], Role.MEMBER)).toBe(true)
    expect(hasAuthority([Role.SYSTEM], Role.MEMBER)).toBe(true)
  })

  it("answers false below the asked-for role", () => {
    expect(hasAuthority([Role.MEMBER], Role.BOARD)).toBe(false)
    expect(hasAuthority([Role.COMMITTEE], Role.BOARD)).toBe(false)
    expect(hasAuthority([Role.GUEST], Role.MEMBER)).toBe(false)
  })

  it("answers false for roles off the chain", () => {
    expect(hasAuthority([Role.VEGAN], Role.MEMBER)).toBe(false)
    expect(hasAuthority([Role.COMPANY], Role.GUEST)).toBe(false)
  })

  it("reads every role held, not just the first", () => {
    expect(hasAuthority([Role.VEGAN, Role.ADMIN], Role.BOARD)).toBe(true)
  })

  it("answers false when nothing is held", () => {
    expect(hasAuthority([], Role.MEMBER)).toBe(false)
    expect(hasAuthority(undefined, Role.MEMBER)).toBe(false)
  })
})
