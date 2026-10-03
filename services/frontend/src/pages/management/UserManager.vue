<script lang="ts" setup>
/* Everyone with an account, in one full-length list: one search over every field, three filters
   and sortable columns. Money is not here; it lives in Contributions. */
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuTrigger} from "reka-ui"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FullList from "@/components/island/FullList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import SortHeader from "@/components/island/SortHeader.vue"
import StateMark, {type StateKind} from "@/components/island/StateMark.vue"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import UserForm from "@/components/form/UserForm.vue"
import {useSubmitFeedback} from "@/composables/formUtils"
import {useUserSelection} from "@/composables/useUserSelection"
import {type Committee, listCommittees} from "@/domains/committees"
import {
  MEMBERSHIP_WORDS,
  MemberType,
  type MembershipResponse,
  type MembershipState,
  NEEDS_LOOK_WORDS,
  type NeedsLook,
  type PeopleSortKey,
  type PersonRow,
  type UserDetailResponse,
  deleteUser,
  filterPeople,
  listMemberships,
  listUsers,
  peopleRows,
  sortPeople,
} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import type {EditableUser} from "@/utils/editableUser"

defineOptions({name: "UserManagerPage"})

const ROW_HEIGHT = 56

const users = ref<UserDetailResponse[]>([])
const memberships = ref<MembershipResponse[]>([])
const committees = ref<Committee[]>([])
const loaded = ref(false)

// Another page sends a person here by the username it knows them by.
const route = useRoute()
const router = useRouter()
const search = ref(typeof route.query.search === "string" ? route.query.search : "")
const membership = ref<string | null>(null)
const type = ref<string | null>(null)
const needs = ref<string | null>(null)
const sortKey = ref<PeopleSortKey>("name")
const descending = ref(false)

const MEMBERSHIP_MARKS: Record<MembershipState, StateKind> = {current: "in-step", pending: "not-created", former: "missing", never: "not-compared"}
const membershipOptions = (Object.keys(MEMBERSHIP_WORDS) as MembershipState[]).map((key) => ({key, label: MEMBERSHIP_WORDS[key]}))
// Picker values come from the generated SDK; NONE is no type anybody holds.
const typeOptions = Object.values(MemberType).filter((one) => one !== MemberType.NONE)
  .map((key) => ({key, label: key.charAt(0) + key.slice(1).toLowerCase()}))
const needsOptions = [
  {key: "any", label: "Any reason"},
  ...(Object.keys(NEEDS_LOOK_WORDS) as NeedsLook[]).map((key) => ({key, label: NEEDS_LOOK_WORDS[key]})),
]

const rows = computed(() => peopleRows(users.value, memberships.value, committees.value))
const shown = computed(() => sortPeople(
  filterPeople(rows.value, {
    search: search.value,
    membership: membership.value as MembershipState | null,
    type: type.value as MemberType | null,
    needs: needs.value as NeedsLook | "any" | null,
  }),
  sortKey.value,
  descending.value,
))

const filtered = computed(() => search.value !== "" || membership.value !== null || type.value !== null || needs.value !== null)

const clearFilters = () => {
  search.value = ""
  membership.value = null
  type.value = null
  needs.value = null
}

const sortBy = (key: PeopleSortKey) => {
  descending.value = sortKey.value === key ? !descending.value : false
  sortKey.value = key
}

const direction = (key: PeopleSortKey) => (sortKey.value === key ? (descending.value ? "desc" : "asc") : null)

// The list takes what the window leaves below the filters, but never less than a few rows.
const listHeight = ref(Math.max(360, globalThis.innerHeight - 330))

const displayedIds = computed(() => shown.value.map((row) => row.id))
const {selectedIdsArray, isSelected, toggle, clear: clearSelection} = useUserSelection(displayedIds)

/** The task page takes the selection by id and says what will happen before anything does. */
const openBulk = (action: "start" | "end") =>
  router.push({path: `/management/users/bulk/${action}`, query: {ids: selectedIdsArray.value.join(","), back: "/management/users"}})

const load = async () => {
  try {
    const [people, held, groups] = await Promise.all([listUsers(), listMemberships(), listCommittees()])
    users.value = people
    memberships.value = held
    committees.value = groups
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loaded.value = true
  }
}

const addOpen = ref(false)
const addModel = ref<EditableUser>(blankUser())
const addForm = ref<InstanceType<typeof UserForm> | null>(null)
const addSaving = ref(false)
const {submitState: addState, showSubmitStatus: addStatus, setSubmitResult: addResult} = useSubmitFeedback()


const acting = ref<{id: number; name: string} | null>(null)
const deleteOpen = ref(false)

function blankUser(): EditableUser {
  return {
    discord: "", email: "", phoneNumber: "", initials: "", firstName: "", lastName: "", username: "",
    newsletter: true, consentPrivacy: false, photoConsent: false, password: "",
  }
}

function openAdd() {
  addModel.value = blankUser()
  addOpen.value = true
}

async function onAddSave() {
  addSaving.value = true
  const saved = await addForm.value?.save()
  addSaving.value = false
  addResult(saved != null)
}

function onSaved(ok: boolean) {
  if (!ok) return
  addOpen.value = false
  void load()
}

const dialogs = {delete: deleteOpen}

const act = (row: PersonRow, dialog: keyof typeof dialogs) => {
  acting.value = {id: row.id, name: row.fullName}
  dialogs[dialog].value = true
}

async function confirmDelete() {
  const target = acting.value
  deleteOpen.value = false
  if (!target) return
  try {
    await deleteUser(target.id)
    users.value = users.value.filter((user) => user.id !== target.id)
  } catch (error) {
    // The row stays: the account is still there.
    $handleNetworkError(error)
  }
}

onMounted(load)
</script>

<template>
  <div
    class="people"
    data-testid="member-manager-table"
  >
    <header class="people__head">
      <h1 class="people__title">
        Users
      </h1>
      <p
        class="people__count"
        data-testid="member-manager-count"
      >
        {{ shown.length === rows.length ? `${rows.length} people` : `${shown.length} of ${rows.length} people` }}
      </p>
      <button
        class="people__add"
        data-testid="member-manager-add-user-btn"
        type="button"
        @click="openAdd"
      >
        Add user
      </button>
    </header>

    <filter-bar
      :active="filtered"
      testid="member-manager-filters"
      @clear="clearFilters"
    >
      <search-box
        v-model="search"
        label="Search for a user"
        testid="member-manager-search-input"
      />
      <filter-picker
        v-model="membership"
        label="Membership"
        :options="membershipOptions"
        testid="member-manager-filter-membership"
      />
      <filter-picker
        v-model="type"
        label="Type"
        :options="typeOptions"
        testid="member-manager-filter-type"
      />
      <filter-picker
        v-model="needs"
        any-label="Anybody"
        label="Needs a look"
        :options="needsOptions"
        testid="member-manager-filter-needs"
      />
    </filter-bar>

    <selection-bar
      :count="selectedIdsArray.length"
      testid="member-manager-selection"
      @clear="clearSelection"
    >
      <button
        class="people__bulk"
        data-testid="bulk-action-start-membership"
        type="button"
        @click="openBulk('start')"
      >
        Start membership
      </button>
      <button
        class="people__bulk"
        data-testid="bulk-action-end-membership"
        type="button"
        @click="openBulk('end')"
      >
        End membership
      </button>
    </selection-bar>

    <div
      class="people__columns"
      role="presentation"
    >
      <span />
      <sort-header
        :direction="direction('name')"
        label="Name"
        testid="member-manager-header-name"
        @sort="sortBy('name')"
      />
      <sort-header
        :direction="direction('membership')"
        label="Membership"
        testid="member-manager-header-status"
        @sort="sortBy('membership')"
      />
      <sort-header
        class="people__wide"
        :direction="direction('memberSince')"
        label="Member since"
        testid="member-manager-header-member-since"
        @sort="sortBy('memberSince')"
      />
      <span class="people__wide">Needs a look</span>
      <span />
    </div>

    <p
      v-if="loaded && shown.length === 0"
      class="people__note"
      data-testid="member-manager-empty"
    >
      Nobody matches.
    </p>

    <full-list
      :height="listHeight"
      :row-height="ROW_HEIGHT"
      :row-key="(row) => row.id"
      :rows="shown"
      testid="member-manager-list"
    >
      <template #row="{row}">
        <div
          class="people__row"
          :data-testid="`member-manager-row-${row.id}`"
        >
          <input
            :aria-label="`Select ${row.fullName}`"
            :checked="isSelected(row.id)"
            :data-testid="`member-manager-checkbox-${row.id}`"
            type="checkbox"
            @change="toggle(row.id)"
          >
          <span class="people__who">
            <router-link
              class="people__name"
              :data-testid="`member-manager-open-${row.id}`"
              :to="`/management/users/${row.id}`"
            >{{ row.fullName }}</router-link>
            <span class="people__sub">@{{ row.username }} · {{ row.email }}</span>
          </span>
          <span
            class="people__membership"
            :data-testid="`member-manager-status-${row.id}`"
          >
            <state-mark :kind="MEMBERSHIP_MARKS[row.membership]">
              {{ MEMBERSHIP_WORDS[row.membership] }}
            </state-mark>
            <span
              v-if="row.type && row.type !== MemberType.REGULAR"
              class="people__sub"
            >{{ row.type.toLowerCase() }}</span>
          </span>
          <span
            class="people__wide people__sub"
            :data-testid="`member-manager-member-since-${row.id}`"
          >{{ row.memberSince ?? "Never" }}</span>
          <span
            class="people__wide people__needs"
            :data-testid="`member-manager-needs-${row.id}`"
          >{{ row.needs.map((reason) => NEEDS_LOOK_WORDS[reason]).join(", ") }}</span>
          <dropdown-menu-root :modal="false">
            <dropdown-menu-trigger
              :aria-label="`Act on ${row.fullName}`"
              class="people__more"
              :data-testid="`member-manager-actions-${row.id}`"
            >
              ⋯
            </dropdown-menu-trigger>
            <dropdown-menu-portal>
              <dropdown-menu-content
                align="end"
                class="island people-menu"
              >
                <dropdown-menu-item as-child>
                  <router-link
                    class="people-menu__item"
                    :data-testid="`member-manager-open-profile-${row.id}`"
                    :to="`/management/users/${row.id}/profile`"
                  >
                    Edit profile
                  </router-link>
                </dropdown-menu-item>
                <dropdown-menu-item
                  class="people-menu__item people-menu__item--danger"
                  :data-testid="`member-manager-delete-btn-${row.id}`"
                  @select="act(row, 'delete')"
                >
                  Delete
                </dropdown-menu-item>
              </dropdown-menu-content>
            </dropdown-menu-portal>
          </dropdown-menu-root>
        </div>
      </template>
    </full-list>

    <deletion-confirmation-dialog
      v-model="deleteOpen"
      :message="acting ? `Are you sure you want to delete ${acting.name}?` : ''"
      title="Confirm User Deletion"
      @confirm="confirmDelete"
    />

    <base-modal
      v-model="addOpen"
      testid="member-manager-add-user-dialog"
      title="Add user"
      show-save
      save-label="Create user"
      save-testid="user-form-submit-btn"
      save-icon="mdi-content-save"
      :save-loading="addSaving"
      :save-submit-state="addState"
      :save-show-status="addStatus"
      show-cancel
      cancel-label="Cancel"
      @save="onAddSave"
      @cancel="addOpen = false"
    >
      <user-form
        ref="addForm"
        v-model="addModel"
        :show-password="true"
        :options="{includeMemberProfile: true, updateKind: 'board', createVia: 'board'}"
        @submitted="onSaved"
      />
    </base-modal>
  </div>
</template>

<style scoped>
.people {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 2rem 2.4rem 3rem;
}

.people__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.6rem 1.2rem;
}

.people__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.people__count,
.people__note {
  margin: 0;
  color: var(--color-ash);
}

.people__add,
.people__bulk {
  padding: 0.4rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.people__add {
  margin-left: auto;
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.people__columns,
.people__row {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) 10rem 7rem 12rem 2.5rem;
  align-items: center;
  gap: 0.8rem;
}

.people__columns {
  padding: 0 0.6rem;
  font-size: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.people__row {
  height: 56px;
  padding: 0 0.6rem;
  border-bottom: 1px solid var(--color-hairline);
}

.people__who,
.people__membership {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.people__name {
  font-weight: 600;
  color: var(--color-chalk);
  text-decoration: none;
}

.people__name,
.people__sub,
.people__needs {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.people__sub,
.people__needs {
  font-size: 0.8rem;
  color: var(--color-ash);
}

.people__needs {
  color: var(--color-warning);
}

.people__more {
  padding: 0;
  border: 0;
  background: none;
  font-size: 1.2rem;
  color: var(--color-chalk);
  cursor: pointer;
}

@media (max-width: 839px) {
  .people {
    padding: 1.2rem 1.1rem 2rem;
  }

  .people__columns,
  .people__row {
    grid-template-columns: 1.6rem minmax(0, 1fr) 7.5rem 2rem;
    gap: 0.5rem;
  }

  .people__wide {
    display: none;
  }
}
</style>

<style>
.people-menu {
  z-index: 1010;
  min-width: 12rem;
  min-height: 0;
  padding: 0.3rem 0;
  background: var(--color-surface);
  border-top: 3px solid var(--color-brand);
  box-shadow: 0 18px 40px rgb(0 0 0 / 45%);
}

.people-menu__item {
  padding: 0.55rem 1rem;
  font-size: 0.9rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.people-menu__item[data-highlighted] {
  background: color-mix(in oklab, var(--color-chalk) 8%, transparent);
}

.people-menu__item--danger {
  color: var(--color-error, #e5484d);
}
</style>
