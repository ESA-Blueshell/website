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
import {useMemberRows, type MemberRow} from "@/composables/useMemberRows"
import {useMemberFilters} from "@/composables/useMemberFilters"
import {usePaidToggle} from "@/composables/usePaidToggle"

export type {MemberRow}

defineOptions({name: "MemberManagerPage"})

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

const {userSearchIndex, rows, isNotableType, typeIcon, typeLabel, statusColor} =
  useMemberRows(users, memberships, paidUserIds)

const {
  searchInput,
  // search/sortKey/sortAsc are not used directly in the template but are accessed
  // by unit tests via wrapper.vm — keep them in scope for test accessibility.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  search,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  sortKey,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  sortAsc,
  memberFilter,
  paidFilter,
  incassoFilter,
  filteredRows,
  toggleSort,
  sortIcon,
} = useMemberFilters(rows, userSearchIndex)

const {isDisabled: toggleDisabled, isSaving, togglePaid, contributionPeriodChanged} =
  usePaidToggle(paidUserIds)

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
            <!-- Toolbar: search + filters + add user. A deliberate responsive
                 layout (no ragged flex-wrap): desktop = one row; mobile = search
                 on its own line, the three filters in one equal-width row, and a
                 full-width Add user button. -->
            <div class="member-manager-toolbar mb-3">
              <v-text-field
                v-model="searchInput"
                class="mm-search"
                clearable
                data-testid="member-manager-search-input"
                :density="toolbarDensity"
                hide-details
                label="Search members"
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
                    <th class="text-right">
                      Role
                    </th>

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
                          text="Manage membership"
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
                      colspan="8"
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
                      <span class="font-mono text-medium-emphasis text-truncate">{{ row.username }}</span>
                      <v-chip
                        v-if="row.role"
                        class="text-capitalize flex-shrink-0"
                        size="x-small"
                        variant="flat"
                      >
                        {{ row.role }}
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
    </div>

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
      width: 190px;
    }
  }

  .mm-add {
    margin-left: auto;
  }
}

// Below the lg breakpoint (where the table becomes the mobile list): stack the
// toolbar into search / a single equal-width filter row / a full-width button.
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
      margin-left: 0;
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
