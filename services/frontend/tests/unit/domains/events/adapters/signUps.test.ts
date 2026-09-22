import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  changeOwnSignUp,
  listOwnSignUps,
  listSignUpsByAccessToken,
  signUpForEvent,
  withdrawSignUp,
} from "@/domains/events/adapters/signUps"
import {
  createEventSignup,
  deleteEventSignup,
  findEventSignUps,
  findEventSignUpsByAccessToken,
  updateEventSignUp,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  createEventSignup: vi.fn(),
  deleteEventSignup: vi.fn(),
  findEventSignUps: vi.fn(),
  findEventSignUpsByAccessToken: vi.fn(),
  updateEventSignUp: vi.fn(),
}))

beforeEach(() => vi.clearAllMocks())

describe("listOwnSignUps", () => {
  it("asks for what one account signed up for from the moment named", async () => {
    vi.mocked(findEventSignUps).mockResolvedValue({data: [{id: 1}]} as never)

    await expect(listOwnSignUps(9, "2026-01-01")).resolves.toHaveLength(1)
    expect(findEventSignUps).toHaveBeenCalledWith({
      query: {from: "2026-01-01", userId: 9},
      throwOnError: true,
    })
  })

  it("answers with an empty listing where the api named none", async () => {
    vi.mocked(findEventSignUps).mockResolvedValue({} as never)

    await expect(listOwnSignUps(9, "2026-01-01")).resolves.toEqual([])
  })
})

describe("listSignUpsByAccessToken", () => {
  it("carries the guest header, since nothing else says who is asking", async () => {
    vi.mocked(findEventSignUpsByAccessToken).mockResolvedValue({data: [{id: 2}]} as never)

    await expect(listSignUpsByAccessToken("tok")).resolves.toHaveLength(1)
    expect(findEventSignUpsByAccessToken).toHaveBeenCalledWith({
      headers: {"X-Guest-Access-Token": "tok"},
      throwOnError: true,
    })
  })

  it("answers with an empty listing where the api named none", async () => {
    vi.mocked(findEventSignUpsByAccessToken).mockResolvedValue({} as never)

    await expect(listSignUpsByAccessToken("tok")).resolves.toEqual([])
  })

  it("throws on a refusal, which the page tells apart from having signed up for nothing", async () => {
    vi.mocked(findEventSignUpsByAccessToken).mockRejectedValue(new Error("refused"))

    await expect(listSignUpsByAccessToken("tok")).rejects.toBeDefined()
  })
})

describe("signUpForEvent", () => {
  it("answers with the sign-up and the token a guest is remembered by", async () => {
    vi.mocked(createEventSignup).mockResolvedValue({
      data: {id: 5},
      headers: {"x-guest-access-token": "guest-token"},
    } as never)

    await expect(signUpForEvent(500, {answers: []} as never)).resolves.toEqual({
      signUp: {id: 5},
      guestAccessToken: "guest-token",
    })
    expect(createEventSignup).toHaveBeenCalledWith({
      path: {eventId: 500},
      body: {answers: []},
      throwOnError: true,
    })
  })

  it("reads the token whichever casing the header came in", async () => {
    vi.mocked(createEventSignup).mockResolvedValue({
      data: {id: 5},
      headers: {"X-Guest-Access-Token": "cased"},
    } as never)

    await expect(signUpForEvent(500, {} as never)).resolves.toMatchObject({
      guestAccessToken: "cased",
    })
  })

  it("reads the first value where the header came as a list", async () => {
    vi.mocked(createEventSignup).mockResolvedValue({
      data: {id: 5},
      headers: {"x-guest-access-token": ["listed", "second"]},
    } as never)

    await expect(signUpForEvent(500, {} as never)).resolves.toMatchObject({
      guestAccessToken: "listed",
    })
  })

  it("answers with no token where an account signed itself up", async () => {
    vi.mocked(createEventSignup).mockResolvedValue({data: {id: 5}, headers: {}} as never)

    await expect(signUpForEvent(500, {} as never)).resolves.toMatchObject({
      guestAccessToken: null,
    })
  })

  it("answers with no token where the api sent no headers at all", async () => {
    vi.mocked(createEventSignup).mockResolvedValue({data: {id: 5}} as never)

    await expect(signUpForEvent(500, {} as never)).resolves.toMatchObject({
      guestAccessToken: null,
    })
  })

  it("answers with no token where the header carried an empty list", async () => {
    vi.mocked(createEventSignup).mockResolvedValue({
      data: {id: 5},
      headers: {"x-guest-access-token": []},
    } as never)

    await expect(signUpForEvent(500, {} as never)).resolves.toMatchObject({
      guestAccessToken: null,
    })
  })
})

describe("changeOwnSignUp", () => {
  it("carries the guest token, because nothing else says the sign-up is theirs", async () => {
    vi.mocked(updateEventSignUp).mockResolvedValue({data: {id: 5}, headers: {}} as never)

    await expect(changeOwnSignUp(500, {version: 1} as never, "held")).resolves.toEqual({
      signUp: {id: 5},
      guestAccessToken: "held",
    })
    expect(updateEventSignUp).toHaveBeenCalledWith({
      path: {eventId: 500},
      headers: {"X-Guest-Access-Token": "held"},
      body: {version: 1},
      throwOnError: true,
    })
  })

  it("sends no header for an account, which the api knows by its login", async () => {
    vi.mocked(updateEventSignUp).mockResolvedValue({data: {id: 5}, headers: {}} as never)

    await expect(changeOwnSignUp(500, {} as never)).resolves.toMatchObject({
      guestAccessToken: null,
    })
    expect(updateEventSignUp).toHaveBeenCalledWith(
      expect.objectContaining({headers: undefined}),
    )
  })

  it("prefers a fresh token over the one that was held", async () => {
    vi.mocked(updateEventSignUp).mockResolvedValue({
      data: {id: 5},
      headers: {"x-guest-access-token": "fresh"},
    } as never)

    await expect(changeOwnSignUp(500, {} as never, "held")).resolves.toMatchObject({
      guestAccessToken: "fresh",
    })
  })
})

describe("withdrawSignUp", () => {
  it("carries the guest token where a guest is withdrawing", async () => {
    vi.mocked(deleteEventSignup).mockResolvedValue({} as never)

    await expect(withdrawSignUp(44, "held")).resolves.toBeUndefined()
    expect(deleteEventSignup).toHaveBeenCalledWith({
      path: {id: 44},
      headers: {"X-Guest-Access-Token": "held"},
      throwOnError: true,
    })
  })

  it("sends no header for an account", async () => {
    vi.mocked(deleteEventSignup).mockResolvedValue({} as never)

    await withdrawSignUp(44)
    expect(deleteEventSignup).toHaveBeenCalledWith(
      expect.objectContaining({headers: undefined}),
    )
  })
})
