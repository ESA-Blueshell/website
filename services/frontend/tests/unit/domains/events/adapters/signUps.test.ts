import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  addSignUpAsBoard,
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
import {aSignUp} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

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
    vi.mocked(findEventSignUps).mockResolvedValue(answer(findEventSignUps, [aSignUp({id: 1})]))

    await expect(listOwnSignUps(9, "2026-01-01")).resolves.toHaveLength(1)
    expect(findEventSignUps).toHaveBeenCalledWith({
      query: {from: "2026-01-01", userId: 9},
      throwOnError: true,
    })
  })

  it("answers with an empty listing where the api named none", async () => {
    vi.mocked(findEventSignUps).mockResolvedValue(emptyAnswer(findEventSignUps))

    await expect(listOwnSignUps(9, "2026-01-01")).resolves.toEqual([])
  })
})

describe("listSignUpsByAccessToken", () => {
  it("carries the guest header, since nothing else says who is asking", async () => {
    vi.mocked(findEventSignUpsByAccessToken).mockResolvedValue(answer(findEventSignUpsByAccessToken, [aSignUp({id: 2})]))

    await expect(listSignUpsByAccessToken("tok")).resolves.toHaveLength(1)
    expect(findEventSignUpsByAccessToken).toHaveBeenCalledWith({
      headers: {"X-Guest-Access-Token": "tok"},
      throwOnError: true,
    })
  })

  it("answers with an empty listing where the api named none", async () => {
    vi.mocked(findEventSignUpsByAccessToken).mockResolvedValue(emptyAnswer(findEventSignUpsByAccessToken))

    await expect(listSignUpsByAccessToken("tok")).resolves.toEqual([])
  })

  it("throws on a refusal, which the page tells apart from having signed up for nothing", async () => {
    vi.mocked(findEventSignUpsByAccessToken).mockRejectedValue(new Error("refused"))

    await expect(listSignUpsByAccessToken("tok")).rejects.toBeDefined()
  })
})

describe("signUpForEvent", () => {
  it("answers with the sign-up and the token a guest is remembered by", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(answer(createEventSignup, aSignUp(), {"x-guest-access-token": "guest-token"}))

    await expect(signUpForEvent(500, {answers: []})).resolves.toEqual({
      signUp: aSignUp(),
      guestAccessToken: "guest-token",
    })
    expect(createEventSignup).toHaveBeenCalledWith({
      path: {eventId: 500},
      body: {answers: []},
      throwOnError: true,
    })
  })

  it("reads the token whichever casing the header came in", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(answer(createEventSignup, aSignUp(), {"X-Guest-Access-Token": "cased"}))

    await expect(signUpForEvent(500, {})).resolves.toMatchObject({
      guestAccessToken: "cased",
    })
  })

  it("reads the first value where the header came as a list", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(answer(createEventSignup, aSignUp(), {"x-guest-access-token": ["listed", "second"]}))

    await expect(signUpForEvent(500, {})).resolves.toMatchObject({
      guestAccessToken: "listed",
    })
  })

  it("answers with no token where an account signed itself up", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(answer(createEventSignup, aSignUp(), {}))

    await expect(signUpForEvent(500, {})).resolves.toMatchObject({
      guestAccessToken: null,
    })
  })

  it("answers with no token where the api sent no headers at all", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(answer(createEventSignup, aSignUp()))

    await expect(signUpForEvent(500, {})).resolves.toMatchObject({
      guestAccessToken: null,
    })
  })

  it("answers with no token where the header carried an empty list", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(answer(createEventSignup, aSignUp(), {"x-guest-access-token": []}))

    await expect(signUpForEvent(500, {})).resolves.toMatchObject({
      guestAccessToken: null,
    })
  })
})

describe("changeOwnSignUp", () => {
  it("carries the guest token, because nothing else says the sign-up is theirs", async () => {
    vi.mocked(updateEventSignUp).mockResolvedValue(answer(updateEventSignUp, aSignUp(), {}))

    await expect(changeOwnSignUp(500, {version: 1}, "held")).resolves.toEqual({
      signUp: aSignUp(),
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
    vi.mocked(updateEventSignUp).mockResolvedValue(answer(updateEventSignUp, aSignUp(), {}))

    await expect(changeOwnSignUp(500, {})).resolves.toMatchObject({
      guestAccessToken: null,
    })
    expect(updateEventSignUp).toHaveBeenCalledWith(
      expect.objectContaining({headers: undefined}),
    )
  })

  it("prefers a fresh token over the one that was held", async () => {
    vi.mocked(updateEventSignUp).mockResolvedValue(answer(updateEventSignUp, aSignUp(), {"x-guest-access-token": "fresh"}))

    await expect(changeOwnSignUp(500, {}, "held")).resolves.toMatchObject({
      guestAccessToken: "fresh",
    })
  })
})

describe("withdrawSignUp", () => {
  it("carries the guest token where a guest is withdrawing", async () => {
    vi.mocked(deleteEventSignup).mockResolvedValue(emptyAnswer(deleteEventSignup))

    await expect(withdrawSignUp(44, "held")).resolves.toBeUndefined()
    expect(deleteEventSignup).toHaveBeenCalledWith({
      path: {id: 44},
      headers: {"X-Guest-Access-Token": "held"},
      throwOnError: true,
    })
  })

  it("sends no header for an account", async () => {
    vi.mocked(deleteEventSignup).mockResolvedValue(emptyAnswer(deleteEventSignup))

    await withdrawSignUp(44)
    expect(deleteEventSignup).toHaveBeenCalledWith(
      expect.objectContaining({headers: undefined}),
    )
  })
})

describe("addSignUpAsBoard", () => {
  it("adds the sign-up for whoever the body names, with the board's choice to email them", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(answer(createEventSignup, aSignUp({id: 8})))

    await expect(addSignUpAsBoard(500, {userId: 9, answers: []}, true)).resolves.toEqual({
      ok: true,
      saved: aSignUp({id: 8}),
    })
    expect(createEventSignup).toHaveBeenCalledWith({
      path: {eventId: 500},
      body: {userId: 9, answers: []},
      query: {notify: true},
    })
  })

  it("answers with the api's own sentence where it refuses", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(
      refusal(createEventSignup, {detail: "This person already has a sign-up for this event."}, 409),
    )

    await expect(addSignUpAsBoard(500, {userId: 9, answers: []}, false)).resolves.toEqual({
      ok: false,
      reason: "This person already has a sign-up for this event.",
    })
  })

  it("says the sign-up could not be added where the api gives no reason", async () => {
    vi.mocked(createEventSignup).mockResolvedValue(refusal(createEventSignup, null, 500))

    await expect(addSignUpAsBoard(500, {userId: 9, answers: []}, false)).resolves.toEqual({
      ok: false,
      reason: "The sign-up could not be added.",
    })
  })
})
