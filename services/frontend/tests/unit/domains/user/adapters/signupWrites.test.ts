import {describe, expect, it, vi} from "vitest"
import {
  readMemberProfile,
  saveAddressChange,
  saveNameOnRosters,
  saveNewAddress,
  saveNewUser,
  saveSignupAddress,
  saveSignupDetails,
  saveUser,
  startSignup,
} from "@/domains/user/adapters/users"
import {
  applyForMembership,
  saveMembership,
  startMembershipAsBoard,
  startOwnMembership,
} from "@/domains/user/adapters/memberships"
import {
  apply,
  boardCreateMembership,
  createAddress,
  createMembership,
  createUser,
  findMemberProfileByUserId,
  saveAddress,
  setNameOnRosters,
  signUp,
  updateAddress,
  updateDetails,
  updateMembership,
  updateUser,
} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"
import {MemberType} from "@/services/api"
import type {CreateUserRequest, SignupAddressRequest} from "@/services/api"
import {aMembership, aMemberProfile, anAddress, aUser} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  apply: vi.fn(),
  boardCreateMembership: vi.fn(),
  createAddress: vi.fn(),
  createMembership: vi.fn(),
  createUser: vi.fn(),
  setNameOnRosters: vi.fn(),
  findMemberProfileByUserId: vi.fn(),
  saveAddress: vi.fn(),
  signUp: vi.fn(),
  updateAddress: vi.fn(),
  updateDetails: vi.fn(),
  updateMembership: vi.fn(),
  updateUser: vi.fn(),
}))

const place: SignupAddressRequest = {street: "Hengelosestraat", houseNumber: "1", zipCode: "7521 AA", city: "Enschede", country: "NL"}

const account: CreateUserRequest = {
  username: "roos",
  email: "roos@esa.test",
  firstName: "Roos",
  initials: "R.",
  lastName: "Kruk",
  discord: "",
  phoneNumber: "",
  newsletter: false,
}

const details = {
  username: "roos",
  firstName: "Roos",
  initials: "R.",
  lastName: "Kruk",
  discord: "",
  phoneNumber: "",
  newsletter: false,
}

describe("the address writes", () => {
  it("records a new address", async () => {
    vi.mocked(createAddress).mockResolvedValue(answer(createAddress, anAddress()))

    await expect(saveNewAddress({...place, userId: 1})).resolves.toEqual(anAddress())
    expect(createAddress).toHaveBeenCalledWith({body: {...place, userId: 1}, throwOnError: true})
  })

  it("records a change against the address the number names", async () => {
    vi.mocked(updateAddress).mockResolvedValue(answer(updateAddress, anAddress()))

    await expect(saveAddressChange(3, {...place, version: 0})).resolves.toEqual(anAddress())
    expect(updateAddress).toHaveBeenCalledWith({
      path: {id: 3},
      body: {...place, version: 0},
      throwOnError: true,
    })
  })

  // An applicant cannot sign in yet, so the address is written against the token instead.
  it("records a signup's address against the token the applicant holds", async () => {
    vi.mocked(saveAddress).mockResolvedValue(emptyAnswer(saveAddress))

    await saveSignupAddress("sel.ver", place)

    expect(saveAddress).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "sel.ver"},
      body: place,
      throwOnError: true,
    })
  })
})

describe("the account writes", () => {
  it("records a new account", async () => {
    vi.mocked(createUser).mockResolvedValue(answer(createUser, aUser()))

    await expect(saveNewUser(account)).resolves.toEqual(aUser())
  })

  it("records a change against the account the number names", async () => {
    const change = {discord: "", phoneNumber: "", newsletter: true, version: 0}
    vi.mocked(updateUser).mockResolvedValue(answer(updateUser, aUser()))

    await expect(saveUser(7, change)).resolves.toEqual(aUser())
    expect(updateUser).toHaveBeenCalledWith({
      path: {id: 7},
      body: change,
      throwOnError: true,
    })
  })

  it("answers a new signup with the session the rest of it is carried out against", async () => {
    const session = {userId: 7, email: "roos@esa.test", signupToken: "sel.ver", expiresAt: "2026-10-01T00:00:00Z"}
    vi.mocked(signUp).mockResolvedValue(answer(signUp, session))

    await expect(startSignup(account)).resolves.toEqual(session)
  })

  it("records a signup's details against the token the applicant holds", async () => {
    vi.mocked(updateDetails).mockResolvedValue(emptyAnswer(updateDetails))

    await saveSignupDetails("sel.ver", details)

    expect(updateDetails).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "sel.ver"},
      body: details,
      throwOnError: true,
    })
  })
})

describe("saveNameOnRosters", () => {
  it("answers what is now stored, or nothing where it was refused", async () => {
    vi.mocked(setNameOnRosters).mockResolvedValueOnce(answer(setNameOnRosters, aUser({nameOnRosters: true})))
    await expect(saveNameOnRosters(42, true)).resolves.toBe(true)
    expect(setNameOnRosters).toHaveBeenCalledWith({path: {userId: 42}, body: {shown: true}})

    vi.mocked(setNameOnRosters).mockResolvedValueOnce(refusal(setNameOnRosters, {status: 403}))
    await expect(saveNameOnRosters(42, false)).resolves.toBeNull()
  })
})

describe("readMemberProfile", () => {
  it("answers with the profile on the account", async () => {
    vi.mocked(findMemberProfileByUserId).mockResolvedValue(
      answer(findMemberProfileByUserId, aMemberProfile({studentNumber: "s123"})),
    )

    await expect(readMemberProfile(42)).resolves.toEqual(aMemberProfile({studentNumber: "s123"}))
    expect(findMemberProfileByUserId).toHaveBeenCalledWith({path: {userId: 42}})
  })

  it("answers with nothing where there is no profile to read", async () => {
    vi.mocked(findMemberProfileByUserId).mockResolvedValue(emptyAnswer(findMemberProfileByUserId))

    await expect(readMemberProfile(42)).resolves.toBeNull()
  })
})

describe("the membership writes", () => {
  it("applies on the token the applicant holds, and answers with what came of it", async () => {
    vi.mocked(apply).mockResolvedValue(answer(apply, {emailConfirmed: false, membershipStarted: false}))

    await expect(applyForMembership("sel.ver", true))
      .resolves.toEqual({emailConfirmed: false, membershipStarted: false})
    expect(apply).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "sel.ver"},
      body: {conditionsAccepted: true},
      throwOnError: true,
    })
  })

  it("applies as a signed-in account", async () => {
    vi.mocked(createMembership).mockResolvedValue(answer(createMembership, {emailConfirmed: true, membershipStarted: true}))

    await expect(startOwnMembership(true)).resolves.toEqual({emailConfirmed: true, membershipStarted: true})
    expect(createMembership).toHaveBeenCalledWith({
      body: {conditionsAccepted: true},
      throwOnError: true,
    })
  })

  it("records a change against the membership the number names", async () => {
    vi.mocked(updateMembership).mockResolvedValue(answer(updateMembership, aMembership({id: 99})))

    await expect(saveMembership(99, {userId: 42, startDate: "2026-09-01", version: 0}))
      .resolves.toEqual(aMembership({id: 99}))
  })

  it("starts a membership on somebody else's behalf", async () => {
    const terms = {userId: 7, memberType: MemberType.REGULAR, startDate: "2026-09-01"}
    vi.mocked(boardCreateMembership).mockResolvedValue(answer(boardCreateMembership, aMembership({id: 33})))

    await expect(startMembershipAsBoard(7, terms)).resolves.toEqual(aMembership({id: 33}))
    expect(boardCreateMembership).toHaveBeenCalledWith({
      path: {userId: 7},
      body: terms,
      throwOnError: true,
    })
  })
})
