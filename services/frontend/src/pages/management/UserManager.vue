<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useSubmitFeedback} from "@/composables/formUtils"
import {useDisplay} from "vuetify"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import ManageMembershipDialog from "@/components/common/modals/ManageMembershipDialog.vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import UserForm from "@/components/form/UserForm.vue"

import {
  deleteUserById,
  findMemberships,
  findUserById,
  findUsers,
} from "@/services/api"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"
import {useUserRows, type MemberRow} from "@/composables/useUserRows"
import {useUserFilters, type SortKey} from "@/composables/useUserFilters"
import {usePaidToggle} from "@/composables/usePaidToggle"
import {useUserSelection} from "@/composables/useUserSelection"
import {computeBulkTargets} from "@/utils/bulkTarget"
import BulkActionsMenu from "@/components/common/BulkActionsMenu.vue"
import PaidStatusDialog from "@/components/common/modals/bulk/PaidStatusDialog.vue"

export type {MemberRow}

defineOptions({name: "UserManagerPage"})

// ── Display ───────────────────────────────────────────────────────────────────

const {lgAndUp} = useDisplay()
const toolbarDensity = computed(() => (lgAndUp.value ? "comfortable" : "compact"))

// ── State ────────────────────────────────────────────────────────────────────

const users = ref<EditableUser[]>([])
const memberships = ref<import("@/services/api").MembershipResponse[]>([])
const paidUserIds = ref<Set<number>>(new Set())

const deleteDialog = ref(false)
const pendingDeleteUser = ref<EditableUser | null>(null)

// Add user dialog
const addDialog = ref(false)
const addModel = ref<EditableUser>(blankUser())
const addFormRef = ref<InstanceType<typeof UserForm> | null>(null)
const addFormSaving = ref(false)
const {submitState: addSubmitState, showSubmitStatus: addShowStatus, setSubmitResult: addSetResult} =
  useSubmitFeedback()

// Edit profile dialog
const editDialog = ref(false)
const editModel = ref<EditableUser | null>(null)
const editFormRef = ref<InstanceType<typeof UserForm> | null>(null)
const editFormSaving = ref(false)
const {submitState: editSubmitState, showSubmitStatus: editShowStatus, setSubmitResult: editSetResult} =
  useSubmitFeedback()

// Manage membership dialog
const manageDialog = ref(false)
const manageUserId = ref<number | null>(null)
const manageUserName = ref("")

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

// ── Composables ───────────────────────────────────────────────────────────────

const {isDisabled: toggleDisabled, isSaving, togglePaid, contributionPeriodChanged, selectedPeriod} =
  usePaidToggle(paidUserIds)

const {userSearchIndex, rows, isNotableType, typeIcon, typeLabel, statusColor} =
  useUserRows(users, memberships, paidUserIds, selectedPeriod)

const {
  searchInput,
  // search is accessed by unit tests via wrapper.vm; keep it in scope.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  search,
  sortKey,
  sortAsc,
  memberFilter,
  paidFilter,
  incassoFilter,
  periodMemberFilter,
  filteredRows,
  toggleSort,
  sortIcon,
} = useUserFilters(rows, userSearchIndex)

// ── Bulk actions ──────────────────────────────────────────────────────────────

// Selection follows the rows on screen, so the header checkbox means "these" rather than
// "everyone", and a filter change never silently drops somebody from the set.
const displayedIds = computed(() => filteredRows.value.map((row) => row.id))
const {
  selectedIdsArray,
  isSelected,
  toggle: toggleSelected,
  toggleHeader,
  headerChecked,
  headerIndeterminate,
  hasSelection,
  clear: clearSelection,
} = useUserSelection(displayedIds)

const bulkAction = ref<"paid" | "unpaid" | null>(null)
const bulkDialogOpen = ref(false)

const membershipsByUserId = computed(() => {
  const byUser = new Map<number, typeof memberships.value>()
  for (const membership of memberships.value) {
    const list = byUser.get(membership.userId) ?? []
    list.push(membership)
    byUser.set(membership.userId, list)
  }
  return byUser
})

const usersById = computed(
  () => new Map(users.value.filter((user) => user.id != null).map((user) => [user.id as number, user])),
)

const bulkTargets = computed(() =>
  computeBulkTargets(selectedIdsArray.value, membershipsByUserId.value, paidUserIds.value, usersById.value),
)

function openBulkAction(action: "paid" | "unpaid") {
  bulkAction.value = action
  bulkDialogOpen.value = true
}

/** The action applied, so the rows it touched are refetched and the selection is spent. */
async function onBulkDone() {
  clearSelection()
  await refreshAfterBulk()
}

/** The api refused the selection because the table was stale: refresh, keep the selection. */
async function onBulkStale() {
  await refreshAfterBulk()
}

/** Reloading the period is what repopulates the paid set, so it stands in for a paid refetch. */
async function refreshAfterBulk() {
  await Promise.all([
    getUsers(),
    getMemberships(),
    contributionPeriodChanged(selectedPeriod.value ?? undefined),
  ])
}

function ariaSort(key: SortKey) {
  if (sortKey.value !== key) return "none"
  return sortAsc.value ? "ascending" : "descending"
}

// Users count badge: show "shown / total" when a filter/search narrows the
// list, otherwise just the total.
const userCountLabel = computed(() =>
  filteredRows.value.length === rows.value.length
    ? `${rows.value.length}`
    : `${filteredRows.value.length} / ${rows.value.length}`,
)

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

// ── Handlers: Add / Edit profile ──────────────────────────────────────────────

function blankUser(): EditableUser {
  return {
    discord: "",
    email: "",
    phoneNumber: "",
    initials: "",
    firstName: "",
    lastName: "",
    username: "",
    newsletter: true,
    consentPrivacy: false,
    photoConsent: false,
    password: "",
  }
}

function openAddUser() {
  addModel.value = blankUser()
  addDialog.value = true
}

async function openEditProfile(row: MemberRow) {
  const resp = await findUserById({path: {userId: row.id}})
  if (resp.data) {
    editModel.value = toEditableUser(resp.data)
    editDialog.value = true
  }
}

function onUserSaved(ok: boolean) {
  if (ok) {
    addDialog.value = false
    getUsers()
  }
}

async function onAddSave() {
  addFormSaving.value = true
  const result = await addFormRef.value?.save()
  addFormSaving.value = false
  addSetResult(result != null)
}

function onProfileSaved(ok: boolean) {
  if (ok) {
    editDialog.value = false
    getUsers()
  }
}

async function onEditSave() {
  editFormSaving.value = true
  const result = await editFormRef.value?.save()
  editFormSaving.value = false
  editSetResult(result != null)
}

// ── Handlers: Manage membership ───────────────────────────────────────────────

function openManageMembership(row: MemberRow) {
  manageUserId.value = row.id
  manageUserName.value = row.fullName
  manageDialog.value = true
}

async function onMembershipChanged() {
  await getMemberships()
  if (manageUserId.value === null) return
  const r = await findUserById({path: {userId: manageUserId.value}})
  if (r.data) updateUser(toEditableUser(r.data))
}

onMounted(async () => {
  try {
    await Promise.all([getUsers(), getMemberships()])
  } catch (error) {
    console.error("Error fetching data:", error)
  }
})

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
</script>

<template>
  <v-main>
    <top-banner title="User Manager" />

    <v-container>
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
            <div class="d-flex align-center mb-4">
              <v-badge
                :content="userCountLabel"
                color="primary"
              >
                <h2 class="ma-0">
                  Users
                </h2>
              </v-badge>
            </div>

            <!-- Toolbar: search + filters, spanning the full width. A deliberate
                 responsive layout (no ragged flex-wrap): desktop = one row; mobile =
                 search on its own line and filters in equal-width rows. -->
            <div class="member-manager-toolbar mb-3">
              <v-text-field
                v-model="searchInput"
                class="mm-search"
                clearable
                data-testid="member-manager-search-input"
                :density="toolbarDensity"
                hide-details
                label="Search users"
                prepend-inner-icon="mdi-magnify"
              />
              <div class="mm-filters">
                <v-select
                  v-model="memberFilter"
                  :items="[{title:'All',value:'all'},{title:'Yes',value:'yes'},{title:'No',value:'no'}]"
                  data-testid="member-manager-filter-membership"
                  :density="toolbarDensity"
                  hide-details
                  label="Membership"
                />
                <v-select
                  v-model="paidFilter"
                  :items="[{title:'All',value:'all'},{title:'Yes',value:'yes'},{title:'No',value:'no'}]"
                  data-testid="member-manager-filter-paid"
                  :density="toolbarDensity"
                  hide-details
                  label="Paid"
                />
                <v-select
                  v-model="incassoFilter"
                  :items="[{title:'All',value:'all'},{title:'Yes',value:'yes'},{title:'No',value:'no'}]"
                  data-testid="member-manager-filter-incasso"
                  :density="toolbarDensity"
                  hide-details
                  label="Incasso"
                />
                <v-select
                  v-model="periodMemberFilter"
                  :items="[{title:'All',value:'all'},{title:'Yes',value:'yes'},{title:'No',value:'no'}]"
                  data-testid="member-manager-filter-period-member"
                  :density="toolbarDensity"
                  hide-details
                  label="Member in period"
                />
              </div>
            </div>

            <!-- Desktop table (lg and up) -->
            <div
              v-if="lgAndUp"
              style="overflow-x: auto"
            >
              <v-table
                density="comfortable"
                class="member-manager-vtable"
              >
                <thead>
                  <tr>
                    <!-- Selects the rows on screen, so a filter never hides part of the selection. -->
                    <th class="mm-select-cell mm-th-checkbox">
                      <v-checkbox-btn
                        data-testid="member-manager-header-checkbox"
                        density="compact"
                        :indeterminate="headerIndeterminate"
                        :model-value="headerChecked"
                        @update:model-value="toggleHeader"
                      />
                    </th>

                    <!-- Sortable: Name -->
                    <th
                      class="sortable-header"
                      data-testid="member-manager-header-name"
                      role="button"
                      tabindex="0"
                      :aria-sort="ariaSort('name')"
                      @click="toggleSort('name')"
                      @keydown.enter="toggleSort('name')"
                      @keydown.space.prevent="toggleSort('name')"
                    >
                      Name
                      <v-icon
                        :icon="sortIcon('name')"
                        size="16"
                      />
                    </th>

                    <th
                      class="sortable-header"
                      data-testid="member-manager-header-username"
                      role="button"
                      tabindex="0"
                      :aria-sort="ariaSort('username')"
                      @click="toggleSort('username')"
                      @keydown.enter="toggleSort('username')"
                      @keydown.space.prevent="toggleSort('username')"
                    >
                      Username
                      <v-icon
                        :icon="sortIcon('username')"
                        size="16"
                      />
                    </th>
                    <th
                      class="sortable-header text-right"
                      data-testid="member-manager-header-role"
                      role="button"
                      tabindex="0"
                      :aria-sort="ariaSort('role')"
                      @click="toggleSort('role')"
                      @keydown.enter="toggleSort('role')"
                      @keydown.space.prevent="toggleSort('role')"
                    >
                      Role
                      <v-icon
                        :icon="sortIcon('role')"
                        size="16"
                      />
                    </th>

                    <!-- Sortable: Membership status -->
                    <th
                      class="sortable-header mm-th-multiline"
                      data-testid="member-manager-header-status"
                      role="button"
                      tabindex="0"
                      :aria-sort="ariaSort('status')"
                      @click="toggleSort('status')"
                      @keydown.enter="toggleSort('status')"
                      @keydown.space.prevent="toggleSort('status')"
                    >
                      Membership status
                      <v-icon
                        :icon="sortIcon('status')"
                        size="16"
                      />
                    </th>

                    <!-- Sortable: Member since -->
                    <th
                      class="sortable-header"
                      data-testid="member-manager-header-member-since"
                      role="button"
                      tabindex="0"
                      :aria-sort="ariaSort('memberSince')"
                      @click="toggleSort('memberSince')"
                      @keydown.enter="toggleSort('memberSince')"
                      @keydown.space.prevent="toggleSort('memberSince')"
                    >
                      Member since
                      <v-icon
                        :icon="sortIcon('memberSince')"
                        size="16"
                      />
                    </th>

                    <th
                      class="sortable-header mm-th-multiline mm-th-period"
                      data-testid="member-manager-header-period-member"
                      role="button"
                      tabindex="0"
                      :aria-sort="ariaSort('wasMemberInPeriod')"
                      @click="toggleSort('wasMemberInPeriod')"
                      @keydown.enter="toggleSort('wasMemberInPeriod')"
                      @keydown.space.prevent="toggleSort('wasMemberInPeriod')"
                    >
                      Member in period
                      <v-icon
                        :icon="sortIcon('wasMemberInPeriod')"
                        size="16"
                      />
                    </th>

                    <th
                      class="sortable-header mm-th-multiline mm-th-period"
                      data-testid="member-manager-header-paid"
                      role="button"
                      tabindex="0"
                      :aria-sort="ariaSort('paid')"
                      @click="toggleSort('paid')"
                      @keydown.enter="toggleSort('paid')"
                      @keydown.space.prevent="toggleSort('paid')"
                    >
                      Paid in period
                      <v-icon
                        :icon="sortIcon('paid')"
                        size="16"
                      />
                    </th>
                    <th>Type / Incasso</th>
                    <th class="mm-th-actions">
                      <div class="mm-th-actions__inner">
                        <span>Actions</span>
                        <bulk-actions-menu
                          :has-selection="hasSelection"
                          :no-period="!selectedPeriod"
                          @add-user="openAddUser"
                          @mark-paid="openBulkAction('paid')"
                          @mark-unpaid="openBulkAction('unpaid')"
                        />
                      </div>
                    </th>
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="row in filteredRows"
                    :key="row.id"
                    :class="{'mm-row--selected': isSelected(row.id)}"
                    :data-testid="`member-manager-row-${row.id}`"
                  >
                    <td class="mm-select-cell">
                      <v-checkbox-btn
                        :data-testid="`member-manager-checkbox-${row.id}`"
                        density="compact"
                        :model-value="isSelected(row.id)"
                        @update:model-value="toggleSelected(row.id)"
                      />
                    </td>

                    <!-- Name -->
                    <td class="font-weight-medium">
                      {{ row.fullName }}
                    </td>

                    <!-- Username -->
                    <td class="font-mono text-medium-emphasis">
                      {{ row.username }}
                    </td>

                    <!-- Role -->
                    <td class="text-right">
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

                    <!-- Member in selected contribution period -->
                    <td :data-testid="`member-manager-period-member-${row.id}`">
                      <v-chip
                        :color="row.wasMemberInPeriod ? 'green' : 'grey'"
                        size="small"
                        variant="flat"
                        style="width: 48px; justify-content: center"
                      >
                        {{ row.wasMemberInPeriod ? "Yes" : "No" }}
                      </v-chip>
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

                    <!-- Actions -->
                    <td>
                      <div class="d-flex align-center gap-1">
                        <v-tooltip
                          :text="row.paid ? 'Mark unpaid' : 'Mark paid'"
                          location="top"
                        >
                          <template #activator="{ props }">
                            <v-btn
                              v-bind="props"
                              :data-testid="`member-manager-toggle-paid-btn-${row.id}`"
                              :disabled="toggleDisabled"
                              :loading="isSaving(row.id)"
                              icon
                              size="small"
                              variant="text"
                              @click="togglePaid(row.id)"
                            >
                              <v-icon
                                :icon="row.paid ? 'mdi-cash-remove' : 'mdi-cash-check'"
                                size="18"
                              />
                            </v-btn>
                          </template>
                        </v-tooltip>

                        <v-tooltip
                          text="Manage memberships"
                          location="top"
                        >
                          <template #activator="{ props }">
                            <v-btn
                              v-bind="props"
                              :data-testid="`member-manager-manage-membership-btn-${row.id}`"
                              icon
                              size="small"
                              variant="text"
                              @click="openManageMembership(row)"
                            >
                              <v-icon
                                icon="mdi-card-account-details"
                                size="18"
                              />
                            </v-btn>
                          </template>
                        </v-tooltip>

                        <v-tooltip
                          text="Edit profile"
                          location="top"
                        >
                          <template #activator="{ props }">
                            <v-btn
                              v-bind="props"
                              :data-testid="`member-manager-edit-profile-btn-${row.id}`"
                              icon
                              size="small"
                              variant="text"
                              @click="openEditProfile(row)"
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
                      colspan="9"
                      class="text-center text-medium-emphasis py-6"
                    >
                      No users found.
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </div>

            <!-- Mobile list (below lg) — list idiom matching Address/Recovery/Contribution managers -->
            <div
              v-else
              data-testid="member-manager-mobile-list"
            >
              <v-list
                v-if="filteredRows.length > 0"
                density="compact"
              >
                <template
                  v-for="(row, index) in filteredRows"
                  :key="row.id"
                >
                  <v-list-item
                    class="member-manager-mobile-row"
                    :data-testid="`member-manager-mobile-row-${row.id}`"
                  >
                    <!-- Line 1: Name (title) + action buttons (append slot) -->
                    <v-list-item-title class="text-truncate">
                      {{ row.fullName }}
                    </v-list-item-title>

                    <template #append>
                      <div class="d-flex align-center flex-shrink-0">
                        <v-btn
                          :data-testid="`member-manager-mobile-toggle-paid-btn-${row.id}`"
                          :disabled="toggleDisabled"
                          :loading="isSaving(row.id)"
                          class="btn-tight"
                          icon
                          size="small"
                          variant="text"
                          @click="togglePaid(row.id)"
                        >
                          <v-icon
                            :icon="row.paid ? 'mdi-cash-remove' : 'mdi-cash-check'"
                            size="18"
                          />
                        </v-btn>
                        <v-btn
                          :data-testid="`member-manager-mobile-manage-membership-btn-${row.id}`"
                          class="btn-tight"
                          icon
                          size="small"
                          variant="text"
                          @click="openManageMembership(row)"
                        >
                          <v-icon
                            icon="mdi-card-account-details"
                            size="18"
                          />
                        </v-btn>
                        <v-btn
                          :data-testid="`member-manager-mobile-edit-profile-btn-${row.id}`"
                          class="btn-tight"
                          icon
                          size="small"
                          variant="text"
                          @click="openEditProfile(row)"
                        >
                          <v-icon
                            icon="mdi-pencil"
                            size="18"
                          />
                        </v-btn>
                        <v-btn
                          :data-testid="`member-manager-mobile-delete-btn-${row.id}`"
                          :disabled="row.role === 'admin'"
                          class="btn-tight"
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
                      </div>
                    </template>

                    <!-- Line 2: Username + role chip -->
                    <v-list-item-subtitle class="d-flex align-center gap-2">
                      <span
                        class="font-mono text-medium-emphasis text-truncate flex-grow-1"
                        style="min-width: 0"
                      >{{ row.username }}</span>
                      <v-chip
                        v-if="row.role"
                        class="text-capitalize flex-shrink-0"
                        size="x-small"
                        variant="flat"
                      >
                        {{ row.role }}
                      </v-chip>
                      <v-chip
                        :color="row.wasMemberInPeriod ? 'green' : 'grey'"
                        size="x-small"
                        variant="flat"
                      >
                        {{ row.wasMemberInPeriod ? "In period" : "Not in period" }}
                      </v-chip>
                    </v-list-item-subtitle>
                  </v-list-item>
                  <v-divider v-if="index < filteredRows.length - 1" />
                </template>
              </v-list>

              <div
                v-else
                class="text-center text-medium-emphasis py-6"
              >
                No users found.
              </div>
            </div>
          </v-card-text>
        </v-card>
      </div>
    </v-container>

    <!-- Delete confirmation dialog -->
    <deletion-confirmation-dialog
      v-model="deleteDialog"
      :message="pendingDeleteUser ? `Are you sure you want to delete ${pendingDeleteUser.fullName} (${pendingDeleteUser.username})?` : ''"
      title="Confirm User Deletion"
      @confirm="confirmDeleteUser"
    />

    <!-- Add user dialog -->
    <base-modal
      v-model="addDialog"
      testid="member-manager-add-user-dialog"
      title="Add user"
      show-save
      save-label="Create user"
      save-testid="user-form-submit-btn"
      save-icon="mdi-content-save"
      :save-loading="addFormSaving"
      :save-submit-state="addSubmitState"
      :save-show-status="addShowStatus"
      show-cancel
      cancel-label="Cancel"
      @save="onAddSave"
      @cancel="addDialog = false"
    >
      <user-form
        ref="addFormRef"
        v-model="addModel"
        :show-password="true"
        :options="{includeMemberProfile: true, updateKind: 'board', createVia: 'board'}"
        @submitted="onUserSaved"
      />
    </base-modal>

    <!-- Edit profile dialog -->
    <base-modal
      v-if="editModel"
      v-model="editDialog"
      testid="member-manager-edit-profile-dialog"
      title="Edit profile"
      show-save
      save-label="Save"
      save-testid="user-form-submit-btn"
      save-icon="mdi-content-save-edit"
      :save-loading="editFormSaving"
      :save-submit-state="editSubmitState"
      :save-show-status="editShowStatus"
      show-cancel
      cancel-label="Cancel"
      @save="onEditSave"
      @cancel="editDialog = false"
    >
      <user-form
        ref="editFormRef"
        v-model="editModel"
        :options="{includeMemberProfile: true, updateKind: 'board', createVia: 'board'}"
        @submitted="onProfileSaved"
      />
    </base-modal>

    <!-- Manage membership dialog -->
    <manage-membership-dialog
      v-if="manageUserId !== null"
      v-model="manageDialog"
      :user-id="manageUserId"
      :user-name="manageUserName"
      @changed="onMembershipChanged"
    />
    <paid-status-dialog
      v-model="bulkDialogOpen"
      :contribution-period-id="selectedPeriod?.id ?? null"
      :target-state="bulkAction ?? 'paid'"
      :targets="bulkTargets"
      @done="onBulkDone"
      @stale="onBulkStale"
    />
  </v-main>
</template>

<style lang="scss" scoped>
.sortable-header {
  cursor: pointer;
  user-select: none;
  vertical-align: bottom;

  &:hover {
    // Tinted with the theme's foreground, not black: on the dark theme a black tint darkens
    // the header instead of lifting it, so the hover read as a smudge. Layered over the
    // surface because the header is sticky and has to stay opaque.
    background:
      linear-gradient(rgba(var(--v-theme-on-surface), 0.06), rgba(var(--v-theme-on-surface), 0.06)),
      rgb(var(--v-theme-surface));
  }
}

// Every header label sits on the same baseline, whatever its cell holds: a one-line label, a
// label wrapped onto three lines, or the select-all checkbox.
.member-manager-vtable thead th {
  vertical-align: bottom;
}

// Without this the control keeps its own minimum height and pushes the checkbox a line above
// the header labels beside it.
.mm-th-checkbox :deep(.v-selection-control) {
  min-height: 0;
}

// The menu sits at the trailing edge of the header row, above the per-row action icons and
// opposite the select-all checkbox. The label keeps the baseline it has in every other cell,
// so the taller button must not centre it.
.mm-th-actions {
  .mm-th-actions__inner {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
  }
}

// Long column headers ("Membership status", "Member in period") wrap onto two
// lines instead of forcing the whole table wider.
.mm-th-multiline {
  white-space: normal;
  max-width: 6.5rem;
  line-height: 1.15;
}

// "Member in period" is a short-value (Yes/No) column — keep it narrow so the
// header wraps cleanly instead of reserving a wide column.
.mm-th-period {
  max-width: 4.5rem;
}

.member-manager-vtable {
  thead th {
    position: sticky;
    top: 0;
    background: rgb(var(--v-theme-surface));
    z-index: 2;
  }
}

// Same reason as the header hover: a black stripe on the dark theme sinks into the background
// rather than separating the rows.
tbody tr:nth-child(odd) {
  background: rgba(var(--v-theme-on-surface), 0.02);
}

tbody tr.mm-row--selected > td {
  background: rgba(var(--v-theme-primary), 0.14);
}

.gap-1 {
  gap: 4px;
}

.gap-3 {
  gap: 12px;
}

// Toolbar: one row on desktop; deliberate stacking (no ragged wrap) on mobile.
.member-manager-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;

  // Search takes a third and the four filters split the rest, so the row spans the
  // card rather than leaving the trailing edge empty.
  .mm-search {
    flex: 1 1 0;
    min-width: 180px;
  }

  .mm-filters {
    display: flex;
    flex: 2 1 0;
    gap: 12px;

    > * {
      flex: 1 1 0;
      min-width: 0;
    }
  }
}

// Below the lg breakpoint (where the table becomes the mobile list): stack the
// toolbar into search / equal-width filter rows.
@media (max-width: 1279px) {
  .member-manager-toolbar {
    flex-direction: column;
    align-items: stretch;

    .mm-search {
      flex: 0 0 auto;
      width: 100%;
    }
  }
}

// Compact, single-line mobile rows (table-like, not tall).
.member-manager-mobile-row {
  min-height: 40px;

  .mm-username {
    min-width: 0;
  }
}

.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}

// The checkbox column carries no label and should not take room from the ones that do. Header
// and body share the column, so they share the centring — otherwise the two rows of checkboxes
// sit a few pixels apart.
.mm-select-cell {
  width: 44px;
  padding-inline: 4px !important;
  text-align: center;

  :deep(.v-selection-control) {
    justify-content: center;
  }
}
</style>
