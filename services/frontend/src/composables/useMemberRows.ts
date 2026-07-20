import {computed, ref, type Ref} from "vue"
import {MemberType, type ContributionPeriodResponse, type MembershipResponse} from "@/services/api"
import {type EditableUser} from "@/utils/editableUser"

// ── Row model ──────────────────────────────────────────────────────────────────

export type MemberStatus = "Current" | "Former" | "Never"

export type MemberRow = {
  id: number
  fullName: string
  username: string
  role: string
  status: MemberStatus
  memberSince: string | null
  latestType: MemberType | null
  latestIncasso: boolean
  paid: boolean
  wasMemberInPeriod: boolean
}

// ── Helpers ────────────────────────────────────────────────────────────────────

export function deriveStatus(ums: MembershipResponse[]): MemberStatus {
  if (ums.length === 0) return "Never"
  if (ums.some((m) => !m.endDate)) return "Current"
  return "Former"
}

export function deriveMemberSince(ums: MembershipResponse[]): string | null {
  if (ums.length === 0) return null
  const first = ums[0]!
  return ums.reduce((min, m) => (m.startDate < min ? m.startDate : min), first.startDate)
}

export function deriveLatestMembership(ums: MembershipResponse[]): MembershipResponse | null {
  if (ums.length === 0) return null
  const first = ums[0]!
  return ums.reduce<MembershipResponse>((latest, m) => (m.startDate > latest.startDate ? m : latest), first)
}

export function isNotableType(row: MemberRow): boolean {
  return row.latestType === MemberType.HONORARY || row.latestType === MemberType.ALUMNI
}

export function typeIcon(row: MemberRow): string {
  if (row.latestType === MemberType.HONORARY) return "mdi-crown"
  if (row.latestType === MemberType.ALUMNI) return "mdi-school"
  return ""
}

export function typeLabel(row: MemberRow): string {
  if (row.latestType === MemberType.HONORARY) return "Honorary member"
  if (row.latestType === MemberType.ALUMNI) return "Alumni member"
  return ""
}

export function statusColor(status: MemberStatus): string {
  if (status === "Current") return "green"
  if (status === "Former") return "orange"
  return "grey"
}

export function overlapsContributionPeriod(
  membership: MembershipResponse,
  period: ContributionPeriodResponse | null,
): boolean {
  return !!period
    && membership.startDate <= period.endDate
    && (membership.endDate == null || membership.endDate >= period.startDate)
}

// ── Composable ─────────────────────────────────────────────────────────────────

export function useMemberRows(
  users: Ref<EditableUser[]>,
  memberships: Ref<MembershipResponse[]>,
  paidUserIds: Ref<Set<number>>,
  selectedPeriod: Ref<ContributionPeriodResponse | null> = ref(null),
) {
  // Precomputed map: userId → their memberships, sorted by startDate DESC (most recent first).
  // This ensures [0] is always the latest membership, enabling O(1) access in BulkTarget computation.
  const membershipsByUserId = computed<Map<number, MembershipResponse[]>>(() => {
    const map = new Map<number, MembershipResponse[]>()
    for (const m of memberships.value) {
      const list = map.get(m.userId)
      if (list) {
        list.push(m)
      } else {
        map.set(m.userId, [m])
      }
    }
    // Sort each user's array by startDate DESC (most recent first)
    for (const list of map.values()) {
      list.sort((a, b) => b.startDate.localeCompare(a.startDate))
    }
    return map
  })

  // Precomputed search haystack per user — recomputes only when the user list changes, not on every keystroke.
  const userSearchIndex = computed<Map<number, string>>(() => {
    const map = new Map<number, string>()
    for (const u of users.value) {
      const haystack = [u.fullName, u.username, u.firstName, u.lastName, u.email, (u as Record<string, unknown>)["discord"], (u as Record<string, unknown>)["phoneNumber"]]
        .filter(Boolean)
        .map(String)
        .join(" ")
        .toLowerCase()
      map.set(u.id as number, haystack)
    }
    return map
  })

  const rows = computed<MemberRow[]>(() =>
    users.value.map((u) => {
      const ums = membershipsByUserId.value.get(u.id as number) ?? []
      const latest = deriveLatestMembership(ums)
      return {
        id: u.id as number,
        fullName: u.fullName ?? "",
        username: u.username ?? "",
        role: u.roles?.at(-1)?.toLocaleLowerCase() ?? "",
        status: deriveStatus(ums),
        memberSince: deriveMemberSince(ums),
        latestType: latest?.memberType ?? null,
        latestIncasso: latest?.incasso ?? false,
        paid: paidUserIds.value.has(u.id as number),
        wasMemberInPeriod: ums.some((m) => overlapsContributionPeriod(m, selectedPeriod.value)),
      }
    }),
  )

  return {
    membershipsByUserId,
    userSearchIndex,
    rows,
    // helpers (useful in template or other composables)
    isNotableType,
    typeIcon,
    typeLabel,
    statusColor,
  }
}
