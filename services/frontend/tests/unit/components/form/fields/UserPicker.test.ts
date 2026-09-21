import {describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import UserPicker from "@/components/form/fields/UserPicker.vue"

vi.mock("@/services/api", () => ({
  findUsers: vi.fn().mockResolvedValue({data: {content: []}}),
  Role: {
    ANONYMOUS: "ANONYMOUS",
    GUEST: "GUEST",
    MEMBER: "MEMBER",
    COMMITTEE: "COMMITTEE",
    BOARD: "BOARD",
    TREASURER: "TREASURER",
    ADMIN: "ADMIN",
    SYSTEM: "SYSTEM",
  },
}))

const member = {id: 1, fullName: "Member Mary", email: "mary@example.com", roles: ["MEMBER"]}
const boardOnly = {id: 2, fullName: "Board Bea", email: "bea@example.com", roles: ["BOARD"]}
const outsider = {id: 3, fullName: "Guest Gordon", email: "gordon@example.com", roles: ["GUEST"]}

function picker(membersOnly: boolean) {
  const wrapper = shallowMount(UserPicker, {props: {membersOnly}})
  return (wrapper.vm as unknown as {isEligible: (u: unknown) => boolean}).isEligible
}

describe("UserPicker eligibility", () => {
  it("lets anybody be picked when the event is open to all", () => {
    const isEligible = picker(false)

    expect(isEligible(outsider)).toBe(true)
  })

  it("refuses somebody without a membership on a members-only event", () => {
    const isEligible = picker(true)

    expect(isEligible(outsider)).toBe(false)
    expect(isEligible(member)).toBe(true)
  })

  it("counts a membership reached through the role chain", () => {
    const isEligible = picker(true)

    expect(isEligible(boardOnly)).toBe(true)
  })
})
