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

// The api answers with inherited roles, so a board member arrives carrying MEMBER as well.
const member = {id: 1, fullName: "Member Mary", email: "mary@example.com", roles: ["ANONYMOUS", "GUEST", "MEMBER"]}
const board = {
  id: 2,
  fullName: "Board Bea",
  email: "bea@example.com",
  roles: ["ANONYMOUS", "GUEST", "MEMBER", "COMMITTEE", "BOARD"],
}
const outsider = {id: 3, fullName: "Guest Gordon", email: "gordon@example.com", roles: ["ANONYMOUS", "GUEST"]}

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

  it("counts a board member, who carries the membership the api sent", () => {
    const isEligible = picker(true)

    expect(isEligible(board)).toBe(true)
  })

  it("survives an account the api answered without roles", () => {
    const isEligible = picker(true)

    expect(isEligible({id: 4, fullName: "Nobody", email: "nobody@example.com"})).toBe(false)
  })
})
