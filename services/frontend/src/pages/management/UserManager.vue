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
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {useUserSelection} from "@/composables/useUserSelection"
import {computeBulkTargets} from "@/utils/bulkTarget"
import BulkActionsMenu from "@/components/common/BulkActionsMenu.vue"
import UserManagerMobileRow from "@/components/common/rows/UserManagerMobileRow.vue"
import UserManagerRow from "@/components/common/rows/UserManagerRow.vue"
import PaidStatusDialog from "@/components/common/modals/bulk/PaidStatusDialog.vue"
import MembershipStatusDialog from "@/components/common/modals/bulk/MembershipStatusDialog.vue"
import PaymentEmailWizard from "@/components/common/modals/bulk/paymentEmail/PaymentEmailWizard.vue"

export type {MemberRow}

defineOptions({name: "UserManagerPage"})

const {height: viewportHeight, lgAndUp} = useDisplay()
const toolbarDensity = computed(() => (lgAndUp.value ? "comfortable" : "compact"))

// A virtual scroller places rows by arithmetic, so every row has to be exactly this tall —
// which is what `density="comfortable"` already renders, and what the row component pins.
const ROW_HEIGHT = 44

// Column widths are declared because the table is laid out fixed: under `table-layout: auto`
// the widths come from whichever rows happen to be mounted, so they would shift as the
// window scrolls. The percentages are the ones the auto layout settled on, so the table
// looks the way it did.
const CHECKBOX_COLUMN_WIDTH = "3.3%"
const ACTIONS_COLUMN_WIDTH = "18.6%"

const HEADER_COLUMNS: ReadonlyArray<{
  label: string
  width: string
  sortKey?: SortKey
  testid?: string
  thClass?: string
}> = [
  {label: "Name", width: "13%", sortKey: "name", testid: "member-manager-header-name"},
  {label: "Username", width: "9.9%", sortKey: "username", testid: "member-manager-header-username"},
  {label: "Role", width: "8.4%", sortKey: "role", testid: "member-manager-header-role", thClass: "text-right"},
  {label: "Membership status", width: "9.5%", sortKey: "status", testid: "member-manager-header-status", thClass: "mm-th-multiline"},
  {label: "Member since", width: "11.7%", sortKey: "memberSince", testid: "member-manager-header-member-since"},
  {label: "Member in period", width: "7.3%", sortKey: "wasMemberInPeriod", testid: "member-manager-header-period-member", thClass: "mm-th-multiline mm-th-period"},
  {label: "Paid in period", width: "8%", sortKey: "paid", testid: "member-manager-header-paid", thClass: "mm-th-multiline mm-th-period"},
  {label: "Type / Incasso", width: "10.3%"},
]

const users = ref<EditableUser[]>([])
const memberships = ref<import("@/services/api").MembershipResponse[]>([])
const paidUserIds = ref<Set<number>>(new Set())

const deleteDialog = ref(false)
const pendingDeleteUser = ref<EditableUser | null>(null)

const addDialog = ref(false)
const addModel = ref<EditableUser>(blankUser())
const addFormRef = ref<InstanceType<typeof UserForm> | null>(null)
const addFormSaving = ref(false)
const {submitState: addSubmitState, showSubmitStatus: addShowStatus, setSubmitResult: addSetResult} =
  useSubmitFeedback()

const editDialog = ref(false)
const editModel = ref<EditableUser | null>(null)
const editFormRef = ref<InstanceType<typeof UserForm> | null>(null)
const editFormSaving = ref(false)
const {submitState: editSubmitState, showSubmitStatus: editShowStatus, setSubmitResult: editSetResult} =
  useSubmitFeedback()

const manageDialog = ref(false)
const manageUserId = ref<number | null>(null)
const manageUserName = ref("")

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

const {
  isDisabled: toggleDisabled,
  isSaving,
  togglePaid,
  contributionPeriodChanged,
  selectedPeriod,
  paidKnown,
  loadFailure: paidLoadFailure,
  saveFailure: paidSaveFailure,
} = usePaidToggle(paidUserIds)

const {userSearchIndex, rows} =
  useUserRows(users, memberships, paidUserIds, selectedPeriod, paidKnown)

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

/**
 * Which bulk dialog is up. The contribution actions are booked against the selected
 * period; the membership ones are not, so the menu offers them whether or not one is
 * picked.
 */
type BulkAction = "paid" | "unpaid" | "end" | "start"

const bulkAction = ref<BulkAction | null>(null)
const bulkDialogOpen = ref(false)

const paymentEmailsOpen = ref(false)

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

function openBulkAction(action: BulkAction) {
  bulkAction.value = action
  bulkDialogOpen.value = true
}

/** The chosen action when it is one the membership dialog handles, else null. */
const membershipAction = computed<"end" | "start" | null>(() =>
  bulkAction.value === "end" || bulkAction.value === "start" ? bulkAction.value : null,
)

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

// Height of the sticky header row, so a short list can be measured exactly.
const HEADER_HEIGHT = 48

// Banner, period picker, card heading and toolbar, measured above the table.
const CHROME_ABOVE_TABLE = 420

// The scroller needs a bounded height to virtualize at all. It takes what the window leaves,
// but never more than the rows actually need — otherwise a filtered-down table reserves a
// screenful of empty space below its last row. A number rather than a CSS length on purpose:
// the scroller falls back to parsing this prop when it cannot measure its container.
const tableHeight = computed(() => {
  const available = Math.max(360, viewportHeight.value - CHROME_ABOVE_TABLE)
  const needed = Math.max(160, filteredRows.value.length * ROW_HEIGHT) + HEADER_HEIGHT
  return Math.min(available, needed)
})

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

function openDeleteUser(user: EditableUser) {
  pendingDeleteUser.value = user
  deleteDialog.value = true
}

function openDeleteRow(row: MemberRow) {
  const user = usersById.value.get(row.id)
  if (user) openDeleteUser(user)
}

async function confirmDeleteUser() {
  if (!pendingDeleteUser.value) return
  deleteDialog.value = false
  try {
    await deleteUserById({path: {userId: pendingDeleteUser.value.id as number}, throwOnError: true})
    users.value = users.value.filter((u) => u.id !== pendingDeleteUser.value!.id)
  } catch (error) {
    // The row stays in the table: the account is still there.
    $handleNetworkError(error)
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

        <v-alert
          v-if="paidLoadFailure"
          class="mt-3"
          data-testid="member-manager-paid-unknown"
          type="warning"
          variant="tonal"
        >
          {{ paidLoadFailure }}
        </v-alert>

        <v-alert
          v-if="paidSaveFailure"
          class="mt-3"
          data-testid="member-manager-paid-refused"
          type="warning"
          variant="tonal"
        >
          {{ paidSaveFailure }}
        </v-alert>

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

            <!-- Desktop table (lg and up). Virtualized: only the rows in the window are
                 mounted, so the table costs the same whether the association has 200
                 members or 2000. -->
            <v-data-table-virtual
              v-if="lgAndUp"
              class="manager-table member-manager-vtable"
              density="comfortable"
              disable-sort
              fixed-header
              :height="tableHeight"
              item-value="id"
              :item-height="ROW_HEIGHT"
              :items="filteredRows"
            >
              <template #headers>
                <tr>
                  <!-- Selects the rows on screen, so a filter never hides part of the selection. -->
                  <th
                    class="mm-select-cell mm-th-checkbox"
                    :style="`width: ${CHECKBOX_COLUMN_WIDTH}`"
                  >
                    <v-checkbox-btn
                      data-testid="member-manager-header-checkbox"
                      density="compact"
                      :indeterminate="headerIndeterminate"
                      :model-value="headerChecked"
                      @update:model-value="toggleHeader"
                    />
                  </th>

                  <th
                    v-for="column in HEADER_COLUMNS"
                    :key="column.label"
                    :aria-sort="column.sortKey ? ariaSort(column.sortKey) : undefined"
                    :class="[column.sortKey && 'sortable-header', column.thClass]"
                    :data-testid="column.testid"
                    :role="column.sortKey ? 'button' : undefined"
                    :style="`width: ${column.width}`"
                    :tabindex="column.sortKey ? 0 : undefined"
                    @click="column.sortKey && toggleSort(column.sortKey)"
                    @keydown.enter="column.sortKey && toggleSort(column.sortKey)"
                    @keydown.space.prevent="column.sortKey && toggleSort(column.sortKey)"
                  >
                    {{ column.label }}
                    <v-icon
                      v-if="column.sortKey"
                      :icon="sortIcon(column.sortKey)"
                      size="16"
                    />
                  </th>

                  <th
                    class="mm-th-actions"
                    :style="`width: ${ACTIONS_COLUMN_WIDTH}`"
                  >
                    <div class="mm-th-actions__inner">
                      <span>Actions</span>
                      <bulk-actions-menu
                        :has-selection="hasSelection"
                        :no-period="!selectedPeriod || !paidKnown"
                        @add-user="openAddUser"
                        @mark-paid="openBulkAction('paid')"
                        @mark-unpaid="openBulkAction('unpaid')"
                        @send-payment-emails="paymentEmailsOpen = true"
                        @end-membership="openBulkAction('end')"
                        @start-membership="openBulkAction('start')"
                      />
                    </div>
                  </th>
                </tr>
              </template>

              <!-- Re-evaluated for every row entering the window while scrolling, so the
                   bindings stay cheap: no per-row work beyond the lookups below. -->
              <template #item="{item, index}">
                <user-manager-row
                  :key="(item as MemberRow).id"
                  :class="index % 2 === 0 ? 'mm-row--odd' : undefined"
                  :row="(item as MemberRow)"
                  :saving="isSaving((item as MemberRow).id)"
                  :selected="isSelected((item as MemberRow).id)"
                  :toggle-disabled="toggleDisabled"
                  @toggle-selection="toggleSelected"
                  @toggle-paid="togglePaid"
                  @manage-membership="openManageMembership"
                  @edit-profile="openEditProfile"
                  @delete="openDeleteRow"
                />
              </template>

              <template #no-data>
                <div class="text-center text-medium-emphasis py-6">
                  No users found.
                </div>
              </template>
            </v-data-table-virtual>

            <!-- Mobile list (below lg) — list idiom matching Address/Recovery/Contribution managers -->
            <div
              v-else
              data-testid="member-manager-mobile-list"
            >
              <!-- The table header carries the select-all and the bulk actions, and there is
                   no header here, so the list states both above itself instead. -->
              <div class="member-manager-mobile-bar">
                <v-checkbox-btn
                  aria-label="Select every member shown"
                  data-testid="member-manager-mobile-header-checkbox"
                  density="compact"
                  :indeterminate="headerIndeterminate"
                  :model-value="headerChecked"
                  @update:model-value="toggleHeader"
                />
                <span class="text-caption text-medium-emphasis flex-grow-1">
                  {{ selectedIdsArray.length ? `${selectedIdsArray.length} selected` : "Select all" }}
                </span>
                <bulk-actions-menu
                  :has-selection="hasSelection"
                  :no-period="!selectedPeriod || !paidKnown"
                  @add-user="openAddUser"
                  @mark-paid="openBulkAction('paid')"
                  @mark-unpaid="openBulkAction('unpaid')"
                  @send-payment-emails="paymentEmailsOpen = true"
                  @end-membership="openBulkAction('end')"
                  @start-membership="openBulkAction('start')"
                />
              </div>

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
                    :saving="isSaving(row.id)"
                    :selected="isSelected(row.id)"
                    :toggle-disabled="toggleDisabled"
                    @toggle-selection="toggleSelected"
                    @toggle-paid="togglePaid"
                    @manage-membership="openManageMembership"
                    @edit-profile="openEditProfile"
                    @delete="openDeleteRow"
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
      v-if="bulkAction === 'paid' || bulkAction === 'unpaid'"
      v-model="bulkDialogOpen"
      :contribution-period-id="selectedPeriod?.id ?? null"
      :target-state="bulkAction"
      :targets="bulkTargets"
      @done="onBulkDone"
      @stale="onBulkStale"
    />
    <!-- Mounted only while chosen: opening it is what asks the api for its preview. -->
    <membership-status-dialog
      v-if="membershipAction"
      v-model="bulkDialogOpen"
      :target-state="membershipAction"
      :targets="bulkTargets"
      @done="onBulkDone"
      @stale="onBulkStale"
    />
    <payment-email-wizard
      v-model="paymentEmailsOpen"
      :period="selectedPeriod"
      :user-ids="selectedIdsArray"
      @done="onBulkDone"
    />
  </v-main>
</template>

<style lang="scss" scoped>
// Every header label sits on the same baseline, whatever its cell holds: a one-line label, a
// label wrapped onto three lines, or the select-all checkbox.
.member-manager-vtable :deep(thead th) {
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
  :deep(table) {
    table-layout: fixed;
  }

  :deep(thead th) {
    background: rgb(var(--v-theme-surface));
  }

  // The sticky header rides the scroller's rubber band: a fling to the top slides the whole
  // table down, so the header leaves its edge and the rows behind it show above it. Chrome
  // ties the bounce to overscroll chaining, so `none` is what removes it — the page scrolls
  // from a gesture outside the table instead of from one that ran the table out of rows.
  :deep(.v-table__wrapper) {
    overscroll-behavior: none;
  }
}

// Same reason as the header hover: a black stripe on the dark theme sinks into the background
// rather than separating the rows. Keyed off the row's index in the list rather than
// `:nth-child`, because a virtual scroller reuses row elements and CSS parity would describe
// the window instead of the list.
tbody tr.mm-row--odd {
  background: rgba(var(--v-theme-on-surface), 0.02);
}

.member-manager-mobile-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-inline: 8px;
  border-bottom: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
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
