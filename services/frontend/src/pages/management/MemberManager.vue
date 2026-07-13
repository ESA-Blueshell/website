<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"

import {
  type ContributionPeriodResponse,
  deleteUserById,
  findContributionsByPeriodId,
  findMemberships,
  findUserById,
  findUsers,
  MemberType,
  type MembershipResponse,
} from "@/services/api"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"
import {filterUsers} from "@/plugins/userFilter"

defineOptions({name: "MemberManagerPage"})

// ── State ────────────────────────────────────────────────────────────────────

const users = ref<EditableUser[]>([])
const memberships = ref<MembershipResponse[]>([])
const paidUserIds = ref<Set<number>>(new Set())

const search = ref("")
const sortKey = ref<"name" | "memberSince" | "status">("name")
const sortAsc = ref(true)

const deleteDialog = ref(false)
const pendingDeleteUser = ref<EditableUser | null>(null)

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

// ── Data loading ─────────────────────────────────────────────────────────────

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) {
    users.value = (response.data?.content ?? []).map((u) => toEditableUser(u))
  } else {
    console.log(response.error)
  }
}

const getMemberships = async () => {
  const response = await findMemberships()
  memberships.value = response.data ?? []
}

const contributionPeriodChanged = async (newPeriod: ContributionPeriodResponse | undefined) => {
  if (!newPeriod) {
    paidUserIds.value = new Set()
    return
  }
  const contributionsResp = await findContributionsByPeriodId({path: {periodId: newPeriod.id as number}})
  const ids = (contributionsResp.data ?? []).map((c) => c.userId)
  paidUserIds.value = new Set(ids)
}

const updateUser = (user: EditableUser) => {
  const index = users.value.findIndex((u) => u.id === user.id)
  if (index === -1) {
    users.value = [...users.value, user]
  } else {
    users.value = [
      ...users.value.slice(0, index),
      user,
      ...users.value.slice(index + 1),
    ]
  }
}

// Used by #386 (manage membership stub) when membership changes are submitted
const _membershipChanged = async (updatedMembership: MembershipResponse) => {
  const index = memberships.value.findIndex((m) => m.id === updatedMembership.id)
  if (index === -1) {
    memberships.value = [...memberships.value, updatedMembership]
  } else {
    memberships.value = [
      ...memberships.value.slice(0, index),
      updatedMembership,
      ...memberships.value.slice(index + 1),
    ]
  }
  // Refresh user to pick up role changes from membership update
  const resp = await findUserById({path: {userId: updatedMembership.userId!}})
  if (resp.data) updateUser(toEditableUser(resp.data))
}

onMounted(async () => {
  try {
    await Promise.all([getUsers(), getMemberships()])
  } catch (error) {
    console.error("Error fetching data:", error)
  }
})

// ── Row model ─────────────────────────────────────────────────────────────────

type MemberStatus = "Current" | "Former" | "Never"

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
}

function userMemberships(userId: number): MembershipResponse[] {
  return memberships.value.filter((m) => m.userId === userId)
}

function deriveStatus(ums: MembershipResponse[]): MemberStatus {
  if (ums.length === 0) return "Never"
  if (ums.some((m) => !m.endDate)) return "Current"
  return "Former"
}

function deriveMemberSince(ums: MembershipResponse[]): string | null {
  if (ums.length === 0) return null
  const first = ums[0]!
  return ums.reduce((min, m) => (m.startDate < min ? m.startDate : min), first.startDate)
}

function deriveLatestMembership(ums: MembershipResponse[]): MembershipResponse | null {
  if (ums.length === 0) return null
  const first = ums[0]!
  return ums.reduce<MembershipResponse>((latest, m) => (m.startDate > latest.startDate ? m : latest), first)
}

const rows = computed<MemberRow[]>(() =>
  users.value.map((u) => {
    const ums = userMemberships(u.id as number)
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
    }
  }),
)

// ── Search & Sort ─────────────────────────────────────────────────────────────

const statusOrder: Record<MemberStatus, number> = {Current: 0, Former: 1, Never: 2}

const filteredRows = computed<MemberRow[]>(() => {
  const searched = filterUsers(rows.value, search.value, {keys: ["fullName", "username", "role"]})

  return [...searched].sort((a, b) => {
    let cmp = 0
    if (sortKey.value === "name") {
      cmp = a.fullName.localeCompare(b.fullName)
    } else if (sortKey.value === "memberSince") {
      const aVal = a.memberSince ?? ""
      const bVal = b.memberSince ?? ""
      cmp = aVal.localeCompare(bVal)
    } else if (sortKey.value === "status") {
      cmp = statusOrder[a.status] - statusOrder[b.status]
    }
    return sortAsc.value ? cmp : -cmp
  })
})

function toggleSort(key: "name" | "memberSince" | "status") {
  if (sortKey.value === key) {
    sortAsc.value = !sortAsc.value
  } else {
    sortKey.value = key
    sortAsc.value = true
  }
}

function sortIcon(key: "name" | "memberSince" | "status"): string {
  if (sortKey.value !== key) return "mdi-unfold-more-horizontal"
  return sortAsc.value ? "mdi-arrow-up" : "mdi-arrow-down"
}

// ── Delete ────────────────────────────────────────────────────────────────────

function openDeleteUser(user: EditableUser) {
  pendingDeleteUser.value = user
  deleteDialog.value = true
}

async function confirmDeleteUser() {
  if (!pendingDeleteUser.value) return
  deleteDialog.value = false
  try {
    await deleteUserById({path: {userId: pendingDeleteUser.value.id as number}})
    users.value = users.value.filter((u) => u.id !== pendingDeleteUser.value!.id)
  } catch (error) {
    console.error("Failed to delete user:", error)
  } finally {
    pendingDeleteUser.value = null
  }
}

// ── Notable type/incasso helpers ──────────────────────────────────────────────

function isNotableType(row: MemberRow): boolean {
  return row.latestType === MemberType.HONORARY || row.latestType === MemberType.ALUMNI
}

function typeIcon(row: MemberRow): string {
  if (row.latestType === MemberType.HONORARY) return "mdi-crown"
  if (row.latestType === MemberType.ALUMNI) return "mdi-school"
  return ""
}

function typeLabel(row: MemberRow): string {
  if (row.latestType === MemberType.HONORARY) return "Honorary member"
  if (row.latestType === MemberType.ALUMNI) return "Alumni member"
  return ""
}

function statusColor(status: MemberStatus): string {
  if (status === "Current") return "green"
  if (status === "Former") return "orange"
  return "grey"
}
</script>

<template>
  <v-main>
    <top-banner title="Member Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-3"
        style="max-width: 1400px"
      >
        <contribution-period-list @update:contribution-period="contributionPeriodChanged" />

        <v-card
          class="mt-3"
          data-testid="member-manager-table"
        >
          <v-card-text>
            <!-- Toolbar: search + add user -->
            <div class="d-flex align-center gap-3 mb-3">
              <v-text-field
                v-model="search"
                clearable
                data-testid="member-manager-search-input"
                density="comfortable"
                hide-details
                label="Search members"
                prepend-inner-icon="mdi-magnify"
                style="max-width: 380px"
              />
              <v-spacer />
              <v-btn
                color="primary"
                data-testid="member-manager-add-user-btn"
                prepend-icon="mdi-plus"
                variant="flat"
              >
                Add user
              </v-btn>
            </div>

            <div style="overflow-x: auto">
              <v-table
                density="comfortable"
                class="member-manager-vtable"
              >
                <thead>
                  <tr>
                    <!-- Sortable: Name -->
                    <th
                      class="sortable-header"
                      data-testid="member-manager-header-name"
                      @click="toggleSort('name')"
                    >
                      Name
                      <v-icon
                        :icon="sortIcon('name')"
                        size="16"
                      />
                    </th>

                    <th>Username</th>
                    <th>Role</th>

                    <!-- Sortable: Status -->
                    <th
                      class="sortable-header"
                      data-testid="member-manager-header-status"
                      @click="toggleSort('status')"
                    >
                      Status
                      <v-icon
                        :icon="sortIcon('status')"
                        size="16"
                      />
                    </th>

                    <!-- Sortable: Member since -->
                    <th
                      class="sortable-header"
                      data-testid="member-manager-header-member-since"
                      @click="toggleSort('memberSince')"
                    >
                      Member since
                      <v-icon
                        :icon="sortIcon('memberSince')"
                        size="16"
                      />
                    </th>

                    <th>Type / Incasso</th>
                    <th>Paid</th>
                    <th>Actions</th>
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="row in filteredRows"
                    :key="row.id"
                    :data-testid="`member-manager-row-${row.id}`"
                  >
                    <!-- Name -->
                    <td class="font-weight-medium">
                      {{ row.fullName }}
                    </td>

                    <!-- Username -->
                    <td class="font-mono text-medium-emphasis">
                      {{ row.username }}
                    </td>

                    <!-- Role -->
                    <td>
                      <v-chip
                        v-if="row.role"
                        size="small"
                        variant="flat"
                        class="text-capitalize"
                      >
                        {{ row.role }}
                      </v-chip>
                    </td>

                    <!-- Status -->
                    <td :data-testid="`member-manager-status-${row.id}`">
                      <v-chip
                        :color="statusColor(row.status)"
                        size="small"
                        variant="flat"
                      >
                        {{ row.status }}
                      </v-chip>
                    </td>

                    <!-- Member since -->
                    <td :data-testid="`member-manager-member-since-${row.id}`">
                      {{ row.memberSince ?? "—" }}
                    </td>

                    <!-- Type / Incasso icons (notable only) -->
                    <td :data-testid="`member-manager-type-incasso-${row.id}`">
                      <div class="d-flex align-center gap-1">
                        <v-tooltip
                          v-if="isNotableType(row)"
                          :text="typeLabel(row)"
                          location="top"
                        >
                          <template #activator="{ props }">
                            <v-icon
                              v-bind="props"
                              :icon="typeIcon(row)"
                              size="18"
                              color="primary"
                            />
                          </template>
                        </v-tooltip>
                        <v-tooltip
                          v-if="row.latestIncasso"
                          text="Incasso active"
                          location="top"
                        >
                          <template #activator="{ props }">
                            <v-icon
                              v-bind="props"
                              icon="mdi-bank-transfer"
                              size="18"
                              color="teal"
                            />
                          </template>
                        </v-tooltip>
                      </div>
                    </td>

                    <!-- Paid/Unpaid -->
                    <td :data-testid="`member-manager-paid-status-${row.id}`">
                      <v-chip
                        :color="row.paid ? 'green' : 'red'"
                        size="small"
                        variant="flat"
                        style="width: 56px; justify-content: center"
                      >
                        {{ row.paid ? "Paid" : "Unpaid" }}
                      </v-chip>
                    </td>

                    <!-- Actions -->
                    <td>
                      <div class="d-flex align-center gap-1">
                        <v-tooltip
                          text="Manage membership (#386)"
                          location="top"
                        >
                          <template #activator="{ props }">
                            <v-btn
                              v-bind="props"
                              :data-testid="`member-manager-manage-membership-btn-${row.id}`"
                              icon
                              size="small"
                              variant="text"
                            >
                              <v-icon
                                icon="mdi-card-account-details"
                                size="18"
                              />
                            </v-btn>
                          </template>
                        </v-tooltip>

                        <v-tooltip
                          text="Edit profile (#387)"
                          location="top"
                        >
                          <template #activator="{ props }">
                            <v-btn
                              v-bind="props"
                              :data-testid="`member-manager-edit-profile-btn-${row.id}`"
                              icon
                              size="small"
                              variant="text"
                            >
                              <v-icon
                                icon="mdi-pencil"
                                size="18"
                              />
                            </v-btn>
                          </template>
                        </v-tooltip>

                        <v-tooltip
                          text="Delete user"
                          location="top"
                        >
                          <template #activator="{ props }">
                            <v-btn
                              v-bind="props"
                              :data-testid="`member-manager-delete-btn-${row.id}`"
                              :disabled="row.role === 'admin'"
                              color="red"
                              icon
                              size="small"
                              variant="text"
                              @click="openDeleteUser(users.find((u) => u.id === row.id)!)"
                            >
                              <v-icon
                                icon="mdi-delete"
                                size="18"
                              />
                            </v-btn>
                          </template>
                        </v-tooltip>
                      </div>
                    </td>
                  </tr>

                  <tr v-if="filteredRows.length === 0">
                    <td
                      colspan="8"
                      class="text-center text-medium-emphasis py-6"
                    >
                      No users found.
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </div>
          </v-card-text>
        </v-card>
      </div>
    </div>

    <!-- Delete confirmation dialog -->
    <deletion-confirmation-dialog
      v-model="deleteDialog"
      :message="pendingDeleteUser ? `Are you sure you want to delete ${pendingDeleteUser.fullName} (${pendingDeleteUser.username})?` : ''"
      title="Confirm User Deletion"
      @confirm="confirmDeleteUser"
    />
  </v-main>
</template>

<style lang="scss" scoped>
.sortable-header {
  cursor: pointer;
  user-select: none;
  white-space: nowrap;

  &:hover {
    background: rgba(0, 0, 0, 0.04);
  }
}

.member-manager-vtable {
  thead th {
    position: sticky;
    top: 0;
    background: rgb(var(--v-theme-surface));
    z-index: 2;
  }
}

tbody tr:nth-child(odd) {
  background: rgba(0, 0, 0, 0.02);
}

.gap-1 {
  gap: 4px;
}

.gap-3 {
  gap: 12px;
}
</style>
