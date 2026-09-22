import {describe, expect, it, vi} from "vitest"
import {
  readMemberProfile,
  saveAddressChange,
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
  signUp,
  updateAddress,
  updateDetails,
  updateMembership,
  updateUser,
} from "@/services/api"
import {SIGNUP_TOKEN_HEADER} from "@/plugins/signupContinuation"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  apply: vi.fn(),
  boardCreateMembership: vi.fn(),
  createAddress: vi.fn(),
  createMembership: vi.fn(),
  createUser: vi.fn(),
  findMemberProfileByUserId: vi.fn(),
  saveAddress: vi.fn(),
  signUp: vi.fn(),
  updateAddress: vi.fn(),
  updateDetails: vi.fn(),
  updateMembership: vi.fn(),
  updateUser: vi.fn(),
}))

const answered = (data: unknown) => ({data}) as never

describe("the address writes", () => {
  it("records a new address", async () => {
    vi.mocked(createAddress).mockResolvedValue(answered({id: 3}))

    await expect(saveNewAddress({userId: 1} as never)).resolves.toEqual({id: 3})
    expect(createAddress).toHaveBeenCalledWith({body: {userId: 1}, throwOnError: true})
  })

  it("records a change against the address the number names", async () => {
    vi.mocked(updateAddress).mockResolvedValue(answered({id: 3}))

    await expect(saveAddressChange(3, {city: "Enschede"} as never)).resolves.toEqual({id: 3})
    expect(updateAddress).toHaveBeenCalledWith({
      path: {id: 3},
      body: {city: "Enschede"},
      throwOnError: true,
    })
  })

  // An applicant cannot sign in yet, so the address is written against the token instead.
  it("records a signup's address against the token the applicant holds", async () => {
    vi.mocked(saveAddress).mockResolvedValue(answered(undefined))

    await saveSignupAddress("sel.ver", {city: "Enschede"} as never)

    expect(saveAddress).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "sel.ver"},
      body: {city: "Enschede"},
      throwOnError: true,
    })
  })
})

describe("the account writes", () => {
  it("records a new account", async () => {
    vi.mocked(createUser).mockResolvedValue(answered({id: 7}))

    await expect(saveNewUser({username: "roos"} as never)).resolves.toEqual({id: 7})
  })

  it("records a change against the account the number names", async () => {
    vi.mocked(updateUser).mockResolvedValue(answered({id: 7}))

    await expect(saveUser(7, {username: "roos"} as never)).resolves.toEqual({id: 7})
    expect(updateUser).toHaveBeenCalledWith({
      path: {id: 7},
      body: {username: "roos"},
      throwOnError: true,
    })
  })

  it("answers a new signup with the session the rest of it is carried out against", async () => {
    vi.mocked(signUp).mockResolvedValue(answered({userId: 7, email: "roos@esa.test"}))

    await expect(startSignup({username: "roos"} as never))
      .resolves.toEqual({userId: 7, email: "roos@esa.test"})
  })

  it("records a signup's details against the token the applicant holds", async () => {
    vi.mocked(updateDetails).mockResolvedValue(answered(undefined))

    await saveSignupDetails("sel.ver", {username: "roos"} as never)

    expect(updateDetails).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "sel.ver"},
      body: {username: "roos"},
      throwOnError: true,
    })
  })
})

describe("readMemberProfile", () => {
  it("answers with the profile on the account", async () => {
    vi.mocked(findMemberProfileByUserId).mockResolvedValue(answered({studentNumber: "s123"}))

    await expect(readMemberProfile(42)).resolves.toEqual({studentNumber: "s123"})
    expect(findMemberProfileByUserId).toHaveBeenCalledWith({path: {userId: 42}})
  })

  it("answers with nothing where there is no profile to read", async () => {
    vi.mocked(findMemberProfileByUserId).mockResolvedValue({} as never)

    await expect(readMemberProfile(42)).resolves.toBeNull()
  })
})

describe("the membership writes", () => {
  it("applies on the token the applicant holds, and answers with what came of it", async () => {
    vi.mocked(apply).mockResolvedValue(answered({emailConfirmed: false, membershipStarted: false}))

    await expect(applyForMembership("sel.ver", true))
      .resolves.toEqual({emailConfirmed: false, membershipStarted: false})
    expect(apply).toHaveBeenCalledWith({
      headers: {[SIGNUP_TOKEN_HEADER]: "sel.ver"},
      body: {conditionsAccepted: true},
      throwOnError: true,
    })
  })

  it("applies as a signed-in account", async () => {
    vi.mocked(createMembership).mockResolvedValue(answered({membershipStarted: true}))

    await expect(startOwnMembership(true)).resolves.toEqual({membershipStarted: true})
    expect(createMembership).toHaveBeenCalledWith({
      body: {conditionsAccepted: true},
      throwOnError: true,
    })
  })

  it("records a change against the membership the number names", async () => {
    vi.mocked(updateMembership).mockResolvedValue(answered({id: 99}))

    await expect(saveMembership(99, {incasso: true} as never)).resolves.toEqual({id: 99})
  })

  it("starts a membership on somebody else's behalf", async () => {
    vi.mocked(boardCreateMembership).mockResolvedValue(answered({id: 33}))

    await expect(startMembershipAsBoard(7, {incasso: false} as never)).resolves.toEqual({id: 33})
    expect(boardCreateMembership).toHaveBeenCalledWith({
      path: {userId: 7},
      body: {incasso: false},
      throwOnError: true,
    })
  })
})
