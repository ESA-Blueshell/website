import {describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import {MemberType} from "@/services/api"
import {
  deriveLatestMembership,
  deriveMemberSince,
  deriveStatus,
  isNotableType,
  statusColor,
  typeIcon,
  typeLabel,
  useMemberRows,
  type MemberRow,
} from "@/composables/useMemberRows"
import type {EditableUser} from "@/utils/editableUser"
import type {ContributionPeriodResponse, MembershipResponse} from "@/services/api"

vi.mock("vuetify", () => ({
  useDisplay: () => ({lgAndUp: {value: true}}),
}))

function makeMembership(overrides: {
  id: number
  userId: number
  startDate: string
  endDate?: string
  memberType?: MemberType
  incasso?: boolean
}): MembershipResponse {
  return {
    id: overrides.id,
    userId: overrides.userId,
    startDate: overrides.startDate,
    endDate: overrides.endDate,
    memberType: overrides.memberType ?? MemberType.REGULAR,
    incasso: overrides.incasso ?? false,
    version: 1,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
  }
}

function makeUser(id: number, fullName: string, username: string, roles: string[] = ["USER"]): EditableUser {
  return {
    id,
    fullName,
    username,
    firstName: fullName.split(" ")[0] ?? "",
    lastName: fullName.split(" ")[1] ?? "",
    email: `${username}@test.com`,
    roles,
    initials: "XX",
    newsletter: false,
    consentPrivacy: false,
    photoConsent: false,
    password: "",
    enabled: true,
    createdAt: "2025-01-01T00:00:00.000Z",
    updatedAt: "2025-01-01T00:00:00.000Z",
    version: 0,
  } as unknown as EditableUser
}

function makeContributionPeriod(overrides: Partial<ContributionPeriodResponse> = {}): ContributionPeriodResponse {
  return {
    alumniFee: 10,
    createdAt: "2025-01-01T00:00:00.000Z",
    endDate: "2024-12-31",
    fullYearFee: 20,
    halfYearFee: 10,
    id: 1,
    startDate: "2024-01-01",
    updatedAt: "2025-01-01T00:00:00.000Z",
    version: 1,
    ...overrides,
  }
}

describe("deriveStatus", () => {
  it("returns Never when no memberships", () => {
    expect(deriveStatus([])).toBe("Never")
  })

  it("returns Current when any membership has no endDate", () => {
    expect(deriveStatus([
      makeMembership({id: 1, userId: 1, startDate: "2020-01-01", endDate: "2021-01-01"}),
      makeMembership({id: 2, userId: 1, startDate: "2023-01-01"}), // active
    ])).toBe("Current")
  })

  it("returns Former when all memberships have endDates", () => {
    expect(deriveStatus([
      makeMembership({id: 1, userId: 1, startDate: "2020-01-01", endDate: "2021-01-01"}),
    ])).toBe("Former")
  })
})

describe("deriveMemberSince", () => {
  it("returns null for empty array", () => {
    expect(deriveMemberSince([])).toBeNull()
  })

  it("returns minimum startDate across all memberships", () => {
    expect(deriveMemberSince([
      makeMembership({id: 1, userId: 1, startDate: "2022-06-01"}),
      makeMembership({id: 2, userId: 1, startDate: "2020-01-01"}),
      makeMembership({id: 3, userId: 1, startDate: "2024-01-01"}),
    ])).toBe("2020-01-01")
  })
})

describe("deriveLatestMembership", () => {
  it("returns null for empty array", () => {
    expect(deriveLatestMembership([])).toBeNull()
  })

  it("returns the membership with max startDate", () => {
    const older = makeMembership({id: 1, userId: 1, startDate: "2020-01-01", memberType: MemberType.REGULAR})
    const newer = makeMembership({id: 2, userId: 1, startDate: "2024-06-01", memberType: MemberType.HONORARY})
    expect(deriveLatestMembership([older, newer])?.id).toBe(2)
  })
})

describe("isNotableType / typeIcon / typeLabel / statusColor", () => {
  const honRow: MemberRow = {id: 1, fullName: "A", username: "a", role: "", status: "Current", memberSince: null, latestType: MemberType.HONORARY, latestIncasso: false, paid: false, wasMemberInPeriod: false}
  const alumRow: MemberRow = {...honRow, latestType: MemberType.ALUMNI}
  const regRow: MemberRow = {...honRow, latestType: MemberType.REGULAR}
  const noneRow: MemberRow = {...honRow, latestType: null}

  it("isNotableType: HONORARY and ALUMNI are notable", () => {
    expect(isNotableType(honRow)).toBe(true)
    expect(isNotableType(alumRow)).toBe(true)
    expect(isNotableType(regRow)).toBe(false)
    expect(isNotableType(noneRow)).toBe(false)
  })

  it("typeIcon returns correct icons", () => {
    expect(typeIcon(honRow)).toBe("mdi-crown")
    expect(typeIcon(alumRow)).toBe("mdi-school")
    expect(typeIcon(regRow)).toBe("")
  })

  it("typeLabel returns correct labels", () => {
    expect(typeLabel(honRow)).toBe("Honorary member")
    expect(typeLabel(alumRow)).toBe("Alumni member")
    expect(typeLabel(regRow)).toBe("")
  })

  it("statusColor returns correct colours", () => {
    expect(statusColor("Current")).toBe("green")
    expect(statusColor("Former")).toBe("orange")
    expect(statusColor("Never")).toBe("grey")
  })
})

describe("useMemberRows", () => {
  it("builds membershipsByUserId map correctly", () => {
    const users = ref([makeUser(1, "Alice Smith", "alice")])
    const memberships = ref([
      makeMembership({id: 10, userId: 1, startDate: "2024-01-01"}),
      makeMembership({id: 11, userId: 1, startDate: "2023-01-01"}),
    ])
    const paidUserIds = ref(new Set<number>())

    const {membershipsByUserId} = useMemberRows(users, memberships, paidUserIds)
    expect(membershipsByUserId.value.get(1)).toHaveLength(2)
    expect(membershipsByUserId.value.get(2)).toBeUndefined()
  })

  it("builds userSearchIndex with all searchable fields lowercased", () => {
    const users = ref([makeUser(1, "Alice Smith", "alice")])
    const memberships = ref<MembershipResponse[]>([])
    const paidUserIds = ref(new Set<number>())

    const {userSearchIndex} = useMemberRows(users, memberships, paidUserIds)
    const haystack = userSearchIndex.value.get(1) ?? ""
    expect(haystack).toContain("alice smith")
    expect(haystack).toContain("alice")
  })

  it("rows derives correct MemberRow for a user with active membership", () => {
    const users = ref([makeUser(1, "Alice Smith", "alice", ["MEMBER"])])
    const memberships = ref([
      makeMembership({id: 10, userId: 1, startDate: "2024-01-01", memberType: MemberType.HONORARY, incasso: true}),
    ])
    const paidUserIds = ref(new Set<number>([1]))

    const {rows} = useMemberRows(users, memberships, paidUserIds)
    expect(rows.value).toHaveLength(1)
    const row = rows.value[0]!
    expect(row.id).toBe(1)
    expect(row.fullName).toBe("Alice Smith")
    expect(row.status).toBe("Current")
    expect(row.latestType).toBe(MemberType.HONORARY)
    expect(row.latestIncasso).toBe(true)
    expect(row.paid).toBe(true)
  })

  it("rows.paid reflects paidUserIds reactivity", () => {
    const users = ref([makeUser(1, "Alice Smith", "alice")])
    const memberships = ref<MembershipResponse[]>([])
    const paidUserIds = ref(new Set<number>())

    const {rows} = useMemberRows(users, memberships, paidUserIds)
    expect(rows.value[0]!.paid).toBe(false)

    paidUserIds.value = new Set([1])
    expect(rows.value[0]!.paid).toBe(true)
  })

  it("rows.wasMemberInPeriod reflects selected contribution period overlap", () => {
    const users = ref([
      makeUser(1, "Current In Period", "current"),
      makeUser(2, "Former Outside Period", "former"),
    ])
    const memberships = ref([
      makeMembership({id: 10, userId: 1, startDate: "2023-09-01"}),
      makeMembership({id: 11, userId: 2, startDate: "2022-01-01", endDate: "2022-12-31"}),
    ])
    const paidUserIds = ref(new Set<number>())
    const selectedPeriod = ref<ContributionPeriodResponse | null>(makeContributionPeriod())

    const {rows} = useMemberRows(users, memberships, paidUserIds, selectedPeriod)
    expect(rows.value.find((row) => row.id === 1)?.wasMemberInPeriod).toBe(true)
    expect(rows.value.find((row) => row.id === 2)?.wasMemberInPeriod).toBe(false)

    selectedPeriod.value = null
    expect(rows.value.every((row) => !row.wasMemberInPeriod)).toBe(true)
  })

  it("rows.memberSince is null when no memberships", () => {
    const users = ref([makeUser(1, "No Membership", "nomem")])
    const memberships = ref<MembershipResponse[]>([])
    const paidUserIds = ref(new Set<number>())

    const {rows} = useMemberRows(users, memberships, paidUserIds)
    expect(rows.value[0]!.memberSince).toBeNull()
  })
})
