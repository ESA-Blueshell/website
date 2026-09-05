import {describe, expect, it} from "vitest"
import {Role, type UserDetailResponse} from "@/services/api"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"

function response(overrides: Partial<UserDetailResponse> = {}): UserDetailResponse {
  return {
    id: 7,
    username: "ada",
    email: "ada@example.com",
    firstName: "Ada",
    lastName: "Lovelace",
    fullName: "Ada Lovelace",
    initials: "A.",
    enabled: true,
    newsletter: false,
    photoConsent: false,
    roles: [Role.USER],
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
    version: 1,
    ...overrides,
  }
}

function editable(overrides: Partial<EditableUser> = {}): EditableUser {
  return {
    username: "ada",
    email: "ada@example.com",
    firstName: "Ada",
    lastName: "Lovelace",
    initials: "A.",
    discord: "",
    phoneNumber: "",
    newsletter: false,
    ...overrides,
  }
}

describe("toEditableUser", () => {
  it("gives the form empty strings where the response has nothing, so neither field binds to null", () => {
    const user = toEditableUser(response({discord: null, phoneNumber: null}))

    expect(user.discord).toBe("")
    expect(user.phoneNumber).toBe("")
  })

  it("takes what the response knows over what the form is holding", () => {
    const user = toEditableUser(
      response({discord: "ada#0001", phoneNumber: "+31600000001"}),
      editable({discord: "stale#9999", phoneNumber: "+31699999999"}),
    )

    expect(user.discord).toBe("ada#0001")
    expect(user.phoneNumber).toBe("+31600000001")
  })

  it("keeps what the form is holding where the response has nothing to say", () => {
    // A response reloaded mid-edit must not blank a field the member has just typed into.
    const user = toEditableUser(
      response({discord: null, phoneNumber: null}),
      editable({discord: "typed#0002", phoneNumber: "+31600000002"}),
    )

    expect(user.discord).toBe("typed#0002")
    expect(user.phoneNumber).toBe("+31600000002")
  })

  it("treats a cleared field on the response as cleared rather than as nothing to say", () => {
    const user = toEditableUser(
      response({discord: "", phoneNumber: ""}),
      editable({discord: "typed#0002", phoneNumber: "+31600000002"}),
    )

    expect(user.discord).toBe("")
    expect(user.phoneNumber).toBe("")
  })

  it("keeps the fields the response does not carry, such as the password a form collected", () => {
    const user = toEditableUser(response(), editable({password: "hunter2"}))

    expect(user.password).toBe("hunter2")
    expect(user.id).toBe(7)
    expect(user.roles).toEqual([Role.USER])
  })
})
