import {describe, expect, it} from "vitest"
import {MemberType} from "@/services/api"
import {memberTypeLabel} from "@/utils/memberType"

describe("memberTypeLabel", () => {
  it("title-cases every member type", () => {
    expect(Object.values(MemberType).map(memberTypeLabel)).toEqual([
      "Alumni",
      "Honorary",
      "Regular",
      "None",
    ])
  })

  it("renders a missing member type as an em dash", () => {
    expect(memberTypeLabel(null)).toBe("—")
    expect(memberTypeLabel(undefined)).toBe("—")
  })
})
