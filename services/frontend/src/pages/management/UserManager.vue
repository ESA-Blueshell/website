<script lang="ts" setup>
/* Everyone with an account, in one full-length list: one search over every field, three filters
   and sortable columns. Money is not here; it lives in Contributions. */
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark, {type StateKind} from "@/components/island/StateMark.vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import UserForm from "@/components/form/UserForm.vue"
import ManagementHead from "@/components/management/ManagementHead.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {useSubmitFeedback} from "@/composables/formUtils"
import {useUserSelection} from "@/composables/useUserSelection"
import {type Committee, listCommittees} from "@/domains/committees"
import {readCurrentPeriod} from "@/domains/contribution"
import {
  MEMBERSHIP_WORDS,
  MemberType,
  type MembershipResponse,
  type MembershipState,
  NEEDS_LOOK_WORDS,
  type NeedsLook,
  type PersonRow,
  type UserDetailResponse,
  filterPeople,
  listMemberships,
  listUsers,
  peopleRows,
  membershipRank,
} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import type {EditableUser} from "@/utils/editableUser"
import {formatDay} from "@/utils/timestamps"

defineOptions({name: "UserManagerPage"})

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

const MEMBERSHIP_MARKS: Record<MembershipState, StateKind> = {current: "in-step", pending: "missing", former: "not-compared", never: "not-compared"}
const MEMBERSHIP_SHORT: Record<MembershipState, string> = {current: "Active", pending: "Pending", former: "Former", never: "Never a member"}
const NEEDS_MARKS: Record<NeedsLook, StateKind> = {"locked": "not-created", "role-waiting": "unreachable", "no-discord": "unreachable", "no-address": "unreachable"}
const MARKED_ROLES = ["admin", "board", "treasurer"]

const COLUMNS: TableColumn<PersonRow>[] = [
  {key: "name", label: "Name", wrap: true, testid: "member-manager-header-name", sortBy: (row) => row.fullName},
  {key: "membership", label: "Membership", wrap: true, testid: "member-manager-header-status", sortBy: membershipRank},
  {key: "committees", label: "Committees", wrap: true, sortBy: (row) => row.committees.join(", ")},
  {key: "discord", label: "Discord", sortBy: (row) => row.discord},
  {key: "needs", label: "Needs a look", wrap: true, sortBy: (row) => row.needs.map((one) => NEEDS_LOOK_WORDS[one]).join(", ")},
]
const membershipOptions = (Object.keys(MEMBERSHIP_WORDS) as MembershipState[]).map((key) => ({key, label: MEMBERSHIP_WORDS[key]}))
// Picker values come from the generated SDK; NONE is no type anybody holds.
const typeOptions = Object.values(MemberType).filter((one) => one !== MemberType.NONE)
  .map((key) => ({key, label: key.charAt(0) + key.slice(1).toLowerCase()}))
const needsOptions = [
  {key: "any", label: "Any reason"},
  ...(Object.keys(NEEDS_LOOK_WORDS) as NeedsLook[]).map((key) => ({key, label: NEEDS_LOOK_WORDS[key]})),
]

const rows = computed(() => peopleRows(users.value, memberships.value, committees.value))
const shown = computed(() => filterPeople(rows.value, {
  search: search.value,
  membership: membership.value as MembershipState | null,
  type: type.value as MemberType | null,
  needs: needs.value as NeedsLook | "any" | null,
}))

const filtered = computed(() => search.value !== "" || membership.value !== null || type.value !== null || needs.value !== null)

const clearFilters = () => {
  search.value = ""
  membership.value = null
  type.value = null
  needs.value = null
}

const typeName = (type: MemberType): string => type.charAt(0) + type.slice(1).toLowerCase()

/** "Regular · since 1 Sep 2024", or nothing for someone never a member. */
const standingOf = (row: PersonRow): string =>
  (row.type && row.memberSince ? `${typeName(row.type)} · since ${formatDay(row.memberSince)}` : "")

const periodStart = ref<string | null>(null)

const facts = computed(() => {
  const members = rows.value.filter((row) => row.membership === "current").length
  const pending = rows.value.filter((row) => row.membership === "pending").length
  const looked = rows.value.filter((row) => row.needs.length > 0)
  const reasons = (Object.keys(NEEDS_LOOK_WORDS) as NeedsLook[]).filter((reason) => looked.some((row) => row.needs.includes(reason)))
  const start = periodStart.value
  return [
    {label: "Members", value: String(members), sub: `${pending} pending their first contribution`, testid: "member-manager-fact-members"},
    start
      ? {label: "New this period", value: String(rows.value.filter((row) => row.memberSince !== null && row.memberSince >= start).length), sub: `Since ${formatDay(start)}`}
      : {label: "Accounts", value: String(rows.value.length), sub: "Members or not"},
    {label: "Needs a look", value: `${looked.length} ${looked.length === 1 ? "person" : "people"}`, sub: reasons.map((reason) => NEEDS_LOOK_WORDS[reason]).join(", ")},
  ]
})

const displayedIds = computed(() => shown.value.map((row) => row.id))
const {selectedIdsArray, isSelected, toggle, headerState, toggleHeader, selectMany, clear: clearSelection} = useUserSelection(displayedIds)

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
  // Only a fact reads it, so the list stands without one.
  periodStart.value = await readCurrentPeriod().then((period) => period?.startDate ?? null, () => null)
}

const addOpen = ref(false)
const addModel = ref<EditableUser>(blankUser())
const addForm = ref<InstanceType<typeof UserForm> | null>(null)
const addSaving = ref(false)
const {submitState: addState, showSubmitStatus: addStatus, setSubmitResult: addResult} = useSubmitFeedback()

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

onMounted(load)
</script>

<template>
  <div
    class="people"
    data-testid="member-manager-table"
  >
    <management-head
      eyebrow="Members"
      title="Users"
    >
      Everyone with an account: who they are, their membership and whether their account is set up. Money lives in
      Contributions.
      <template #actions>
        <cut-button
          testid="member-manager-add-user-btn"
          @click="openAdd"
        >
          Add a user
        </cut-button>
      </template>
    </management-head>

    <div class="people__body">
      <fact-list
        class="people__facts"
        :facts="facts"
      />

      <management-table
        :columns="COLUMNS"
        :row-key="(row) => row.id"
        :row-testid="(row) => `member-manager-row-${row.id}`"
        :rows="shown"
        :start-sort="{key: 'name'}"
        testid="member-manager-list"
        :header-state="headerState"
        :selected-count="selectedIdsArray.length"
        :total="rows.length"
        :to="(row) => `/management/users/${row.id}`"
        @clear-selection="clearSelection"
        @select-all="selectMany(rows.map((row) => row.id))"
        @toggle-shown="toggleHeader"
      >
        <template #count>
          <span data-testid="member-manager-count"><b>{{ shown.length }}</b> of {{ rows.length }} people</span>
        </template>
        <template #filters>
          <filter-bar
            :active="filtered"
            testid="member-manager-filters"
            @clear="clearFilters"
          >
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
        </template>
        <template #search>
          <search-box
            v-model="search"
            label="Search for a user"
            testid="member-manager-search-input"
          />
        </template>
        <template
          v-if="loaded"
          #empty
        >
          <span data-testid="member-manager-empty">Nobody matches.</span>
        </template>
        <template #check="{row}">
          <row-check
            :checked="isSelected(row.id)"
            :label="`Select ${row.fullName}`"
            :testid="`member-manager-checkbox-${row.id}`"
            @toggle="toggle(row.id)"
          />
        </template>
        <template #name="{row}">
          <router-link
            class="mg-name"
            :data-testid="`member-manager-open-${row.id}`"
            :to="`/management/users/${row.id}`"
          >
            {{ row.fullName }}
          </router-link>
          <role-mark
            v-if="MARKED_ROLES.includes(row.role)"
            class="people__role"
            :role="row.role.charAt(0).toUpperCase() + row.role.slice(1)"
          />
          <span class="mg-sub">{{ row.username }}</span>
        </template>
        <template #membership="{row}">
          <state-mark
            :kind="MEMBERSHIP_MARKS[row.membership]"
            :testid="`member-manager-status-${row.id}`"
          >
            {{ MEMBERSHIP_SHORT[row.membership] }}
          </state-mark>
          <span class="mg-sub">{{ standingOf(row) }}</span>
        </template>
        <template #committees="{row}">
          <span :class="{'mg-quiet': row.committees.length === 0}">{{ row.committees.join(", ") || "·" }}</span>
        </template>
        <template #discord="{row}">
          <span :class="{'mg-quiet': !row.discord}">{{ row.discord ? `@${row.discord}` : "·" }}</span>
        </template>
        <template #needs="{row}">
          <span
            class="people__needs"
            :data-testid="`member-manager-needs-${row.id}`"
          >
            <state-mark
              v-for="reason in row.needs"
              :key="reason"
              :kind="NEEDS_MARKS[reason]"
            >
              {{ NEEDS_LOOK_WORDS[reason] }}
            </state-mark>
            <span
              v-if="row.needs.length === 0"
              class="mg-quiet"
            >·</span>
          </span>
        </template>
        <template #phone="{row}">
          <management-row
            :meta="[standingOf(row), ...row.committees].filter(Boolean).join(' · ')"
            :name="row.fullName"
            :testid="`member-manager-row-${row.id}`"
            :to="`/management/users/${row.id}`"
          >
            <template #check>
              <row-check
                :checked="isSelected(row.id)"
                :label="`Select ${row.fullName}`"
                :testid="`member-manager-checkbox-${row.id}`"
                @toggle="toggle(row.id)"
              />
            </template>
            <state-mark
              v-if="row.needs.length === 0"
              :kind="MEMBERSHIP_MARKS[row.membership]"
            >
              {{ MEMBERSHIP_SHORT[row.membership] }}
            </state-mark>
            <state-mark
              v-else
              :kind="NEEDS_MARKS[row.needs[0]!]"
            >
              {{ NEEDS_LOOK_WORDS[row.needs[0]!] }}
            </state-mark>
          </management-row>
        </template>
      </management-table>

      <selection-bar
        always
        :count="selectedIdsArray.length"
        testid="member-manager-selection"
        @clear="clearSelection"
      >
        <cut-button
          small
          testid="bulk-action-start-membership"
          tone="solid"
          @click="openBulk('start')"
        >
          Start membership
        </cut-button>
        <cut-button
          small
          testid="bulk-action-end-membership"
          @click="openBulk('end')"
        >
          End membership
        </cut-button>
      </selection-bar>
    </div>

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
.people__body {
  padding: 0 2.4rem 3rem;
}

.people__facts {
  padding: 1.1rem 0 1.2rem;
}

.people__count {
  margin: 1rem 0 0;
  padding: 0 0.2rem 0.5rem;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.people__count b {
  color: var(--color-chalk);
}

.people__note {
  color: var(--color-ash);
}

.people__role {
  margin-left: 0.4rem;
  vertical-align: 0.1em;
}

.people__needs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.1rem 0.9rem;
}

@media (--phone) {
  .people__body {
    padding: 0 1.1rem 2rem;
  }
}
</style>
