<script lang="ts" setup>
import {computed, onMounted, onBeforeUnmount, ref, shallowRef} from "vue"
import {useSubmitFeedback} from "@/composables/formUtils"
import {useDisplay} from "vuetify"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import ManageMembershipDialog from "@/components/common/modals/ManageMembershipDialog.vue"
import BulkActionsMenu from "@/components/common/BulkActionsMenu.vue"
import PaidStatusDialog from "@/components/common/modals/bulk/PaidStatusDialog.vue"
import EndMembershipDialog from "@/components/common/modals/bulk/EndMembershipDialog.vue"
import ResumeMembershipDialog from "@/components/common/modals/bulk/ResumeMembershipDialog.vue"
import ReminderDialog from "@/components/common/modals/bulk/ReminderDialog.vue"
import IncassoDialog from "@/components/common/modals/bulk/IncassoDialog.vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import UserForm from "@/components/form/UserForm.vue"
import UserManagerRow from "@/components/common/rows/UserManagerRow.vue"
import UserManagerMobileRow from "@/components/common/rows/UserManagerMobileRow.vue"

import {
  deleteUserById,
  findMemberships,
  findUserById,
  findUsers,
  findContributionPeriods,
} from "@/services/api"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"
import {useUserRows, type MemberRow} from "@/composables/useUserRows"
import {useUserFilters, type SortKey} from "@/composables/useUserFilters"
import {usePaidToggle} from "@/composables/usePaidToggle"
import {useUserSelection} from "@/composables/useUserSelection"
import {computeBulkTargets, amsterdamToday, latestPeriodOf, type BulkTarget} from "@/utils/bulkTarget"
import {isClickOnInteractiveTarget} from "@/utils/rowInteraction"

export type {MemberRow}

defineOptions({name: "UserManagerPage"})

// ── Display ───────────────────────────────────────────────────────────────────

const {lgAndUp} = useDisplay()
const toolbarDensity = computed(() => (lgAndUp.value ? "comfortable" : "compact"))

// ── State ────────────────────────────────────────────────────────────────────

const users = ref<EditableUser[]>([])
const memberships = ref<import("@/services/api").MembershipResponse[]>([])
// shallowRef: usePaidToggle always reassigns a fresh Set (never mutates in
// place), so deep-proxying every Set read is pure overhead on the row hot path.
const paidUserIds = shallowRef<Set<number>>(new Set())

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

// Bulk action dialog — one per-action dialog component, chosen by the menu.
type BulkActionKind =
  | "markPaid"
  | "markUnpaid"
  | "sendReminder"
  | "sendIncasso"
  | "endMembership"
  | "resumeMembership"

const bulkDialogOpen = ref(false)
const bulkAction = ref<BulkActionKind>("markPaid")
const allPeriods = ref<import("@/services/api").ContributionPeriodResponse[]>([])

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

// ── Composables ───────────────────────────────────────────────────────────────

const {isDisabled: toggleDisabled, isSaving, togglePaid, contributionPeriodChanged, reloadPaid, selectedPeriod} =
  usePaidToggle(paidUserIds)

// isNotableType, typeIcon, typeLabel, statusColor are used by unit tests that
// access wrapper.vm directly; they remain in scope even though the template
// delegates rendering to UserManagerRow / UserManagerMobileRow.
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const {membershipsByUserId, userSearchIndex, rows, isNotableType, typeIcon, typeLabel, statusColor} =
  useUserRows(users, memberships, paidUserIds, selectedPeriod)

const {
  searchInput,
  // search is accessed by unit tests via wrapper.vm; keep it in scope.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  search,
  sortKey,
  sortAsc,
  membershipStatusFilter,
  paidFilter,
  incassoFilter,
  periodMemberFilter,
  filteredRows,
  toggleSort,
  sortIcon,
} = useUserFilters(rows, userSearchIndex)

// Derive displayed IDs from filteredRows for the selection composable
const displayedIds = computed(() => filteredRows.value.map((r) => r.id))

const {
  selectedIdsArray,
  hasSelection,
  isSelected,
  toggle: toggleSelection,
  headerChecked,
  headerIndeterminate,
  toggleHeader,
  clear: clearSelection,
} = useUserSelection(displayedIds)

// Vuetify v-data-table-virtual density="compact" → --v-table-row-height: 36px
const ROW_HEIGHT = 36

// Sortable header columns configuration
interface HeaderColumn {
  label: string
  sortKey: SortKey | null
  testid: string
  width: string
  thClass?: string
}

const HEADER_COLUMNS: HeaderColumn[] = [
  {label: "Name", sortKey: "name", testid: "member-manager-header-name", width: "19%"},
  {label: "Username", sortKey: "username", testid: "member-manager-header-username", width: "15%"},
  {label: "Role", sortKey: "role", testid: "member-manager-header-role", width: "8%", thClass: "text-right"},
  {label: "Membership status", sortKey: "status", testid: "member-manager-header-status", width: "10%", thClass: "mm-th-multiline"},
  {label: "Member since", sortKey: "memberSince", testid: "member-manager-header-member-since", width: "10%"},
  {label: "Member in period", sortKey: "wasMemberInPeriod", testid: "member-manager-header-period-member", width: "8%", thClass: "mm-th-multiline mm-th-period"},
  {label: "Paid in period", sortKey: "paid", testid: "member-manager-header-paid", width: "8%", thClass: "mm-th-multiline mm-th-period"},
]

function ariaSort(key: SortKey) {
  if (sortKey.value !== key) return "none"
  return sortAsc.value ? "ascending" : "descending"
}

// Users count badge: show "shown / total" when a filter/search narrows the
// list, otherwise just the total.
const memberCountLabel = computed(() =>
  filteredRows.value.length === rows.value.length
    ? `${rows.value.length}`
    : `${filteredRows.value.length} / ${rows.value.length}`,
)

// Whether period-relative bulk actions are disabled (no period selected)
const noPeriodSelected = computed(() => !selectedPeriod.value)

// Build a map of users for BulkTarget computation
const usersById = computed<Map<number, EditableUser>>(() => {
  const map = new Map<number, EditableUser>()
  for (const u of users.value) {
    if (u.id != null) map.set(u.id, u)
  }
  return map
})

// Compute bulk targets from selected IDs
const bulkTargets = computed<BulkTarget[]>(() =>
  computeBulkTargets(
    selectedIdsArray.value,
    membershipsByUserId.value,
    paidUserIds.value,
    usersById.value,
  ),
)

// Get today's date in Amsterdam timezone
const serverToday = computed(() => amsterdamToday())

// Get the latest period for resume-membership classification
const latestPeriod = computed(() => latestPeriodOf(allPeriods.value))

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

const getContributionPeriods = async () => {
  const response = await findContributionPeriods()
  allPeriods.value = response.data ?? []
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

// ── Bulk action handlers ──────────────────────────────────────────────────────

function openBulkAction(action: BulkActionKind) {
  bulkAction.value = action
  bulkDialogOpen.value = true
}

async function onBulkDone() {
  clearSelection()
  await Promise.all([getUsers(), getMemberships(), reloadPaid()])
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

onMounted(async () => {
  try {
    await Promise.all([getUsers(), getMemberships(), getContributionPeriods()])
  } catch (error) {
    console.error("Error fetching data:", error)
  }
})

onBeforeUnmount(() => {
  clearSelection()
})

// ── Delete ────────────────────────────────────────────────────────────────────

function openDeleteUser(user: EditableUser) {
  pendingDeleteUser.value = user
  deleteDialog.value = true
}

// Row-level delete: resolve the user via the usersById map. The rows' @delete
// binding is re-created for every row entering the virtual render window, so
// it must be a stable reference doing an O(1) lookup — an inline
// `users.find(...)` closure here costs O(N) per visible row per scroll step.
function onDeleteRow(row: MemberRow) {
  const user = usersById.value.get(row.id)
  if (user) openDeleteUser(user)
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

// ── Selection mode row click handling ──────────────────────────────────────────

/**
 * Handle row click for selection mode.
 * Only toggles selection if:
 * 1. Selection mode is active (at least one member selected)
 * 2. Click is not on an interactive control
 */
function onRowClick(event: MouseEvent, rowId: number) {
  // Only toggle if at least one member is selected (selection mode active)
  if (selectedIdsArray.value.length === 0) return

  // Ignore clicks on interactive controls
  if (isClickOnInteractiveTarget(event.target as HTMLElement)) return

  toggleSelection(rowId)
}
</script>

<template>
  <v-main>
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
                :content="memberCountLabel"
                color="primary"
              >
                <h2 class="ma-0">
                  Users
                </h2>
              </v-badge>
            </div>

            <!-- Toolbar: search + filters + add user + bulk menu. A deliberate responsive
                 layout (no ragged flex-wrap): desktop = one row; mobile = search
                 on its own line, filters in equal-width rows, and a full-width
                 Add user button. -->
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
                  v-model="membershipStatusFilter"
                  :items="[{title:'All',value:'all'},{title:'Current',value:'current'},{title:'Former',value:'former'},{title:'Never',value:'never'}]"
                  data-testid="member-manager-filter-membership"
                  :density="toolbarDensity"
                  hide-details
                  label="Membership status"
                />
                <v-select
                  v-model="paidFilter"
                  :items="[{title:'All',value:'all'},{title:'Yes',value:'yes'},{title:'No',value:'no'}]"
                  data-testid="member-manager-filter-paid"
                  :density="toolbarDensity"
                  hide-details
                  label="Paid in period"
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
              <v-btn
                class="mm-add"
                color="primary"
                data-testid="member-manager-add-user-btn"
                prepend-icon="mdi-plus"
                variant="flat"
                @click="openAddUser"
              >
                Add user
              </v-btn>
            </div>

            <!-- Desktop table (lg and up) — v-data-table-virtual for row virtualization -->
            <v-data-table-virtual
              v-if="lgAndUp"
              :items="filteredRows"
              :item-height="ROW_HEIGHT"
              :height="600"
              density="compact"
              fixed-header
              :disable-sort="true"
              class="member-manager-vtable"
            >
              <!-- Fully-custom header row: sortable ths, select-all checkbox, bulk-actions menu. -->
              <template #headers>
                <tr>
                  <!-- Select-all header checkbox -->
                  <th
                    class="mm-th-checkbox"
                    style="width: 48px; padding-right: 0"
                  >
                    <v-checkbox
                      :indeterminate="headerIndeterminate"
                      :model-value="headerChecked"
                      color="primary"
                      data-testid="member-manager-header-checkbox"
                      density="compact"
                      hide-details
                      @update:model-value="toggleHeader"
                    />
                  </th>

                  <!-- Sortable headers (v-for) -->
                  <th
                    v-for="col in HEADER_COLUMNS"
                    :key="col.testid"
                    :class="['sortable-header', col.thClass]"
                    :style="`width: ${col.width}`"
                    :data-testid="col.testid"
                    role="button"
                    tabindex="0"
                    :aria-sort="col.sortKey ? ariaSort(col.sortKey) : 'none'"
                    @click="col.sortKey && toggleSort(col.sortKey)"
                    @keydown.enter="col.sortKey && toggleSort(col.sortKey)"
                    @keydown.space.prevent="col.sortKey && toggleSort(col.sortKey)"
                  >
                    {{ col.label }}
                    <v-icon
                      v-if="col.sortKey"
                      :icon="sortIcon(col.sortKey)"
                      size="16"
                    />
                  </th>

                  <!-- Type / Incasso (non-sortable) -->
                  <th style="width: 7%">
                    Type / Incasso
                  </th>

                  <!-- Actions column: under table-layout: fixed this width is ENFORCED
                       (auto layout used to stretch it to fit content). Four 32px icon
                       buttons + gaps + cell padding need ~160px or they get clipped. -->
                  <th
                    class="text-right"
                    style="width: 160px"
                  >
                    <!-- Bulk actions triple-dot menu, right side of the header row. -->
                    <bulk-actions-menu
                      :disabled="!hasSelection"
                      :no-period="noPeriodSelected"
                      @mark-paid="openBulkAction('markPaid')"
                      @mark-unpaid="openBulkAction('markUnpaid')"
                      @send-reminder="openBulkAction('sendReminder')"
                      @send-incasso="openBulkAction('sendIncasso')"
                      @end-membership="openBulkAction('endMembership')"
                      @resume-membership="openBulkAction('resumeMembership')"
                    />
                  </th>
                </tr>
              </template>

              <!-- Virtual item row slot — renders existing UserManagerRow unchanged.
                   This slot re-evaluates for every row entering the render window
                   while scrolling, so bindings must stay allocation-light: no
                   inline closures doing O(N) lookups per row. -->
              <template #item="{item, index}">
                <user-manager-row
                  :key="(item as MemberRow).id"
                  :class="index % 2 === 0 ? 'mm-data-row mm-row--odd' : 'mm-data-row'"
                  :row="item as MemberRow"
                  :selected="isSelected((item as MemberRow).id)"
                  :selection-active="hasSelection"
                  :toggle-disabled="toggleDisabled"
                  :is-saving="isSaving((item as MemberRow).id)"
                  @toggle-selection="toggleSelection"
                  @row-click="onRowClick"
                  @toggle-paid="togglePaid"
                  @manage-membership="openManageMembership"
                  @edit-profile="openEditProfile"
                  @delete="onDeleteRow"
                />
              </template>

              <!-- Empty state -->
              <template #no-data>
                <span class="text-medium-emphasis">No users found.</span>
              </template>
            </v-data-table-virtual>

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
                  <user-manager-mobile-row
                    :row="row"
                    @manage-membership="openManageMembership"
                    @edit-profile="openEditProfile"
                    @delete="onDeleteRow"
                  />
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
        :options="{includeMemberProfile: true, updateKind: 'board'}"
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
        :options="{includeMemberProfile: true, updateKind: 'board'}"
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

    <!-- Bulk action dialogs — mounted only when their action is active AND open. -->
    <paid-status-dialog
      v-if="(bulkAction === 'markPaid' || bulkAction === 'markUnpaid') && bulkDialogOpen"
      v-model="bulkDialogOpen"
      :target-state="bulkAction === 'markPaid' ? 'paid' : 'unpaid'"
      :targets="bulkTargets"
      :contribution-period-id="selectedPeriod?.id ?? null"
      @done="onBulkDone"
    />

    <reminder-dialog
      v-if="bulkAction === 'sendReminder' && bulkDialogOpen"
      v-model="bulkDialogOpen"
      :targets="bulkTargets"
      :period="selectedPeriod"
      :server-today="serverToday"
      :latest-period="latestPeriod"
      @done="onBulkDone"
    />

    <incasso-dialog
      v-if="bulkAction === 'sendIncasso' && bulkDialogOpen"
      v-model="bulkDialogOpen"
      :targets="bulkTargets"
      :period="selectedPeriod"
      :server-today="serverToday"
      :latest-period="latestPeriod"
      @done="onBulkDone"
    />

    <end-membership-dialog
      v-if="bulkAction === 'endMembership' && bulkDialogOpen"
      v-model="bulkDialogOpen"
      :targets="bulkTargets"
      :server-today="serverToday"
      @done="onBulkDone"
    />

    <resume-membership-dialog
      v-if="bulkAction === 'resumeMembership' && bulkDialogOpen"
      v-model="bulkDialogOpen"
      :targets="bulkTargets"
      :latest-period="latestPeriod"
      @done="onBulkDone"
    />
  </v-main>
</template>

<style lang="scss" scoped>
.sortable-header {
  cursor: pointer;
  user-select: none;
  vertical-align: bottom;

  &:hover {
    background: linear-gradient(rgba(0, 0, 0, 0.04), rgba(0, 0, 0, 0.04)), rgb(var(--v-theme-surface));
  }
}

// Align checkbox center in cell; remove dense margins so it lines up with header label baseline.
.mm-th-checkbox {
  vertical-align: bottom;
  text-align: center;

  :deep(.v-selection-control) {
    justify-content: center;
    min-height: 0;
  }

  :deep(.v-input__details) {
    display: none;
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

// v-data-table-virtual uses fixed-header which handles sticky thead via its own
// .v-table--fixed-header CSS. The surface background on th ensures header stays
// opaque even when rows scroll underneath it.
// table-layout: fixed ensures columns maintain consistent widths derived from
// the first row (headers), not from rendered row content. This prevents visible
// width jumping as content enters/leaves the virtual render window.
.member-manager-vtable {
  :deep(table) {
    table-layout: fixed;
  }

  :deep(thead th) {
    background: rgb(var(--v-theme-surface));
  }
}

// Explicit class-based striping — index-based so recycled virtual rows keep correct parity.
tbody tr.mm-row--odd {
  background: rgba(0, 0, 0, 0.02);
}

// Fixed height matches density="compact" --v-table-row-height: 36px.
tbody tr.mm-data-row {
  height: 36px;
}

// Selected row highlight
.mm-row--selected > td {
  background: rgba(var(--v-theme-primary), 0.07) !important;
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

  .mm-search {
    flex: 1 1 260px;
    max-width: 480px;
    min-width: 180px;
  }

  .mm-filters {
    display: flex;
    gap: 12px;

    > * {
      width: 170px;
    }
  }

  .mm-add {
    flex: 0 0 auto;
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
      max-width: none;
    }

    .mm-filters > * {
      flex: 1 1 0;
      width: auto;
      min-width: 0;
    }

    .mm-add {
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
</style>
