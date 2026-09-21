import {beforeEach, describe, expect, it, vi} from "vitest"

const {mockDeleteEventSignup, mockFindEventSignUpsByEventId, mockUpdateEventSignUpById} = vi.hoisted(() => ({
  mockDeleteEventSignup: vi.fn(),
  mockFindEventSignUpsByEventId: vi.fn(),
  mockUpdateEventSignUpById: vi.fn(),
}))

vi.mock("@/services/api", () => ({
  deleteEventSignup: mockDeleteEventSignup,
  findEventSignUpsByEventId: mockFindEventSignUpsByEventId,
  updateEventSignUpById: mockUpdateEventSignUpById,
  EventSignUpKind: {GUEST: "GUEST", NON_MEMBER: "NON_MEMBER", MEMBER: "MEMBER"},
}))

const {EventSignUpKind, listEventSignUps, removeSignUp, saveSignUpAsBoard} = await import("@/domains/events")

describe("the domain's public surface", () => {
  it("re-exports the kind enum, so a page never reaches the generated client for it", () => {
    expect(EventSignUpKind.MEMBER).toBe("MEMBER")
    expect(EventSignUpKind.GUEST).toBe("GUEST")
    expect(EventSignUpKind.NON_MEMBER).toBe("NON_MEMBER")
  })
})

describe("listEventSignUps", () => {
  beforeEach(() => vi.clearAllMocks())

  it("asks for the sign-ups of one event", async () => {
    mockFindEventSignUpsByEventId.mockResolvedValue({data: [{id: 1}]})

    await expect(listEventSignUps(55)).resolves.toEqual([{id: 1}])
    expect(mockFindEventSignUpsByEventId).toHaveBeenCalledWith({path: {eventId: 55}})
  })

  it("answers null when the api refused, which is not the same as nobody", async () => {
    mockFindEventSignUpsByEventId.mockResolvedValue({error: {status: 403}})

    await expect(listEventSignUps(55)).resolves.toBeNull()
  })

  it("answers an empty list when the api sent no body", async () => {
    mockFindEventSignUpsByEventId.mockResolvedValue({data: undefined})

    await expect(listEventSignUps(55)).resolves.toEqual([])
  })
})

describe("removeSignUp", () => {
  beforeEach(() => vi.clearAllMocks())

  it("passes the notify choice through", async () => {
    mockDeleteEventSignup.mockResolvedValue({data: undefined})

    await removeSignUp(7, true)

    expect(mockDeleteEventSignup).toHaveBeenCalledWith({
      path: {id: 7},
      query: {notify: true},
      throwOnError: true,
    })
  })

  it("throws what the api answered rather than swallowing it", async () => {
    mockDeleteEventSignup.mockRejectedValue(new Error("403"))

    await expect(removeSignUp(7, false)).rejects.toThrow("403")
  })
})

describe("saveSignUpAsBoard", () => {
  beforeEach(() => vi.clearAllMocks())

  it("addresses the sign-up by id and answers the saved sign-up", async () => {
    mockUpdateEventSignUpById.mockResolvedValue({data: {id: 7, version: 2}})

    await expect(saveSignUpAsBoard(7, {answers: [], version: 1})).resolves.toEqual({id: 7, version: 2})
    expect(mockUpdateEventSignUpById).toHaveBeenCalledWith({
      path: {id: 7},
      body: {answers: [], version: 1},
      throwOnError: true,
    })
  })

  it("throws a refusal rather than answering an empty sign-up", async () => {
    mockUpdateEventSignUpById.mockRejectedValue(new Error("409"))

    await expect(saveSignUpAsBoard(7, {answers: []})).rejects.toThrow("409")
  })
})
