<script lang="ts" setup>
/* Helping people into their account: activation for accounts not yet activated, a password reset
   for active ones, and restoring deleted ones inside their window. Every email opens before it goes. */
import {computed, onMounted, ref} from "vue"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark, {type StateKind} from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import RecoveryAction from "@/components/management/RecoveryAction.vue"
import {listLastRecoveryEmails, listPendingActivations, type TokenPurpose} from "@/domains/recovery"
import {NEEDS_LOOK_WORDS, type NeedsLook, type UserDetailResponse, fold, listDeletedUsers, listUsers, needsLookOf} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {formatDay} from "@/utils/timestamps"

defineOptions({name: "RecoveryManagerPage"})

type Standing = "not-activated" | "active" | "deleted"

interface RecoveryRow {
  user: UserDetailResponse
  standing: Standing
  lastEmail: string | null
  needs: NeedsLook[]
}

const STANDING_WORDS: Record<Standing, string> = {"not-activated": "Not activated", "active": "Active", "deleted": "Deleted"}
const ACTION: Record<Standing, "activation" | "password" | "restore"> = {"not-activated": "activation", "active": "password", "deleted": "restore"}

const STANDING_MARKS: Record<Standing, StateKind> = {"not-activated": "missing", "active": "in-step", "deleted": "not-created"}

const COLUMNS: TableColumn<RecoveryRow>[] = [
  {key: "name", label: "Name", wrap: true, testid: "recovery-sort-name", sortBy: (row) => row.user.fullName},
  {key: "account", label: "Account", wrap: true, sortBy: (row) => STANDING_WORDS[row.standing]},
  {key: "lastEmail", label: "Last recovery email", testid: "recovery-sort-last-email", newestFirst: true, sortBy: (row) => row.lastEmail ?? NEVER},
]

const users = ref<UserDetailResponse[]>([])
const deleted = ref<UserDetailResponse[]>([])
const pending = ref<Record<number, TokenPurpose>>({})
const lastEmails = ref<Record<number, string>>({})
const loaded = ref(false)

const search = ref("")
const standing = ref<string | null>(null)
const needs = ref<string | null>(null)
// Nobody written to yet orders before anybody who was.
const NEVER = "0"

const standingOptions = (Object.keys(STANDING_WORDS) as Standing[]).map((key) => ({key, label: STANDING_WORDS[key]}))
const needsOptions = [
  {key: "any", label: "Any reason"},
  ...(Object.keys(NEEDS_LOOK_WORDS) as NeedsLook[]).map((key) => ({key, label: NEEDS_LOOK_WORDS[key]})),
]

const rows = computed<RecoveryRow[]>(() => [
  ...users.value.map((user) => ({user, standing: (user.enabled ? "active" : "not-activated") as Standing})),
  ...deleted.value.map((user) => ({user, standing: "deleted" as Standing})),
].map((row) => ({...row, lastEmail: lastEmails.value[row.user.id] ?? null, needs: needsLookOf(row.user)})))

const shown = computed(() => {
  const words = fold(search.value).split(" ").filter(Boolean)
  return rows.value.filter((row) => {
    const haystack = fold(`${row.user.fullName} ${row.user.firstName} ${row.user.lastName} ${row.user.username} ${row.user.email}`)
    return words.every((word) => haystack.includes(word))
      && (standing.value === null || row.standing === standing.value)
      && (needs.value === null || (needs.value === "any" ? row.needs.length > 0 : row.needs.includes(needs.value as NeedsLook)))
  })
})

const filtered = computed(() => search.value !== "" || standing.value !== null || needs.value !== null)

const clear = () => {
  search.value = ""
  standing.value = null
  needs.value = null
}

const daysLeft = (user: UserDetailResponse): number | null =>
  user.restoreUntilAt ? Math.ceil((new Date(user.restoreUntilAt).getTime() - Date.now()) / 86_400_000) : null

/** What stands under an account's state: how long a deleted one can still be restored, or what an active one has. */
const whyOf = (row: RecoveryRow): string => {
  if (row.standing === "active") return row.user.twoFactorOn ? "Two-factor on" : ""
  if (row.standing === "not-activated") return "Has not confirmed their email address"
  const left = daysLeft(row.user)
  if (left === null) return ""
  return left > 0 ? `Restorable for ${left} more day${left === 1 ? "" : "s"}` : "Window passed"
}

const facts = computed(() => {
  const count = (standing: Standing) => rows.value.filter((row) => row.standing === standing).length
  const restorable = rows.value.filter((row) => row.standing === "deleted" && (daysLeft(row.user) ?? 0) > 0).length
  return [
    {label: "Not activated", value: String(count("not-activated")), testid: "recovery-fact-not-activated"},
    {label: "Deleted", value: String(count("deleted")), sub: `${restorable} can still be restored`, testid: "recovery-fact-deleted"},
    {label: "Active", value: String(count("active")), testid: "recovery-fact-active"},
  ]
})

const load = async () => {
  try {
    const [people, gone, activations, emails] = await Promise.all([listUsers(), listDeletedUsers(), listPendingActivations(), listLastRecoveryEmails()])
    users.value = people
    deleted.value = gone
    pending.value = activations
    lastEmails.value = emails
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loaded.value = true
  }
}

onMounted(load)
</script>

<template>
  <management-page
    eyebrow="Members"
    testid="recovery-manager"
    title="Account recovery"
  >
    <template #lede>
      Help someone into their account: send an activation email or a password reset, or restore a deleted account. Each
      email opens first, and goes only once you send it from there.
    </template>

    <fact-list
      class="recovery__facts"
      :facts="facts"
    />

    <management-table
      :columns="COLUMNS"
      :row-key="(row) => `${row.standing}-${row.user.id}`"
      :row-testid="(row) => `recovery-user-row-${row.user.id}`"
      :rows="shown"
      :start-sort="{key: 'name'}"
      testid="recovery-list"
      :to="(row) => (row.standing === 'deleted' ? null : `/management/users/${row.user.id}/account`)"
    >
      <template #count>
        <span><b>{{ shown.length }}</b> of {{ rows.length }} accounts</span>
      </template>
      <template #filters>
        <filter-bar
          :active="filtered"
          testid="recovery-filters"
          @clear="clear"
        >
          <filter-picker
            v-model="standing"
            label="State"
            :options="standingOptions"
            testid="recovery-filter-state"
          />
          <filter-picker
            v-model="needs"
            any-label="Anybody"
            label="Needs a look"
            :options="needsOptions"
            testid="recovery-filter-needs"
          />
        </filter-bar>
      </template>
      <template #search>
        <search-box
          v-model="search"
          label="Search for a user"
          testid="recovery-search"
        />
      </template>
      <template
        v-if="loaded"
        #empty
      >
        <span data-testid="recovery-empty">Nobody matches.</span>
      </template>
      <template #name="{row}">
        <router-link
          v-if="row.standing !== 'deleted'"
          class="mg-name"
          :to="`/management/users/${row.user.id}/account`"
        >
          {{ row.user.fullName }}
        </router-link>
        <span
          v-else
          class="mg-name"
        >{{ row.user.fullName }}</span>
        <span class="mg-sub">{{ row.user.username }}</span>
      </template>
      <template #account="{row}">
        <state-mark
          :kind="STANDING_MARKS[row.standing]"
          :testid="`recovery-state-${row.user.id}`"
        >
          {{ STANDING_WORDS[row.standing] }}
        </state-mark>
        <span class="mg-why">{{ whyOf(row) }}</span>
      </template>
      <template #lastEmail="{row}">
        <span
          :class="{'mg-quiet': !row.lastEmail}"
          :data-testid="`recovery-last-email-${row.user.id}`"
        >{{ row.lastEmail ? formatDay(row.lastEmail) : "None yet" }}</span>
      </template>
      <template #acts="{row}">
        <recovery-action
          :action="ACTION[row.standing]"
          :pending-activation="pending[row.user.id] ?? null"
          :user="row.user"
          @done="load"
        />
      </template>
      <template #phone="{row}">
        <management-row
          :meta="[row.user.username, whyOf(row)].filter(Boolean).join(' · ')"
          :name="row.user.fullName"
          :testid="`recovery-user-row-${row.user.id}`"
        >
          <state-mark
            :kind="STANDING_MARKS[row.standing]"
            :testid="`recovery-state-${row.user.id}`"
          >
            {{ STANDING_WORDS[row.standing] }}
          </state-mark>
          <template #acts>
            <recovery-action
              :action="ACTION[row.standing]"
              :pending-activation="pending[row.user.id] ?? null"
              :user="row.user"
              @done="load"
            />
          </template>
        </management-row>
      </template>
    </management-table>
  </management-page>
</template>

<style scoped>
.recovery__facts {
  padding: 1.1rem 0 1.2rem;
}

.recovery__count {
  padding: 1rem 0.2rem 0.5rem;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.recovery__count b {
  color: var(--color-chalk);
}

.recovery__note {
  color: var(--color-ash);
}
</style>
