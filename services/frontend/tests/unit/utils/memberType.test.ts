import {describe, it, expect} from "vitest"
import {memberTypeLabel} from "@/utils/memberType"
import {MemberType} from "@/services/api"

describe("memberTypeLabel", () => {
  it("maps REGULAR to 'Regular'", () => {
    expect(memberTypeLabel(MemberType.REGULAR)).toBe("Regular")
  })

  it("maps ALUMNI to 'Alumni'", () => {
    expect(memberTypeLabel(MemberType.ALUMNI)).toBe("Alumni")
  })

  it("maps HONORARY to 'Honorary'", () => {
    expect(memberTypeLabel(MemberType.HONORARY)).toBe("Honorary")
  })

  it("maps NONE to 'None'", () => {
    expect(memberTypeLabel(MemberType.NONE)).toBe("None")
  })

  it("returns '—' for null", () => {
    expect(memberTypeLabel(null)).toBe("—")
  })

  it("returns '—' for undefined", () => {
    expect(memberTypeLabel(undefined)).toBe("—")
  })

  it("title-cases unknown values outside the enum", () => {
    expect(memberTypeLabel("unknown")).toBe("Unknown")
    expect(memberTypeLabel("CUSTOM")).toBe("Custom")
  })
})
