<script lang="ts" setup>
/* Helping people into their account: activation for accounts not yet activated, a password reset
   for active ones, and restoring deleted ones inside their window. Every email opens before it goes. */
import {computed, onMounted, ref} from "vue"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FullList from "@/components/island/FullList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SortHeader from "@/components/island/SortHeader.vue"
import RecoveryAction from "@/components/management/RecoveryAction.vue"
import {listLastRecoveryEmails, listPendingActivations, type TokenPurpose} from "@/domains/recovery"
import {NEEDS_LOOK_WORDS, type NeedsLook, type UserDetailResponse, fold, listDeletedUsers, listUsers, needsLookOf} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {formatDateNoSeconds} from "@/utils/timestamps"

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

const users = ref<UserDetailResponse[]>([])
const deleted = ref<UserDetailResponse[]>([])
const pending = ref<Record<number, TokenPurpose>>({})
const lastEmails = ref<Record<number, string>>({})
const loaded = ref(false)

const search = ref("")
const standing = ref<string | null>(null)
const needs = ref<string | null>(null)
const sortKey = ref<"name" | "lastEmail">("name")
const descending = ref(false)

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
  const matching = rows.value.filter((row) => {
    const haystack = fold(`${row.user.fullName} ${row.user.firstName} ${row.user.lastName} ${row.user.username} ${row.user.email}`)
    return words.every((word) => haystack.includes(word))
      && (standing.value === null || row.standing === standing.value)
      && (needs.value === null || (needs.value === "any" ? row.needs.length > 0 : row.needs.includes(needs.value as NeedsLook)))
  })
  // A date sorts as a date, and an account never written to sorts before any that was.
  const sorted = [...matching].sort((a, b) => (sortKey.value === "lastEmail"
    ? (a.lastEmail ?? "").localeCompare(b.lastEmail ?? "")
    : a.user.fullName.localeCompare(b.user.fullName)))
  return descending.value ? sorted.reverse() : sorted
})

const filtered = computed(() => search.value !== "" || standing.value !== null || needs.value !== null)

const clear = () => {
  search.value = ""
  standing.value = null
  needs.value = null
}

const sortBy = (key: "name" | "lastEmail") => {
  // A date starts newest first; a name starts at the top of the alphabet.
  descending.value = sortKey.value === key ? !descending.value : key === "lastEmail"
  sortKey.value = key
}

const direction = (key: "name" | "lastEmail") => (sortKey.value === key ? (descending.value ? "desc" : "asc") : null)

const daysLeft = (user: UserDetailResponse): number | null =>
  user.restoreUntilAt ? Math.ceil((new Date(user.restoreUntilAt).getTime() - Date.now()) / 86_400_000) : null

const standingOf = (row: RecoveryRow): string => {
  if (row.standing !== "deleted") return STANDING_WORDS[row.standing]
  const left = daysLeft(row.user)
  if (left === null) return "Deleted"
  return left > 0 ? `Deleted, ${left} day${left === 1 ? "" : "s"} left` : "Deleted, window passed"
}

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

const listHeight = ref(Math.max(360, globalThis.innerHeight - 330))

onMounted(load)
</script>

<template>
  <div
    class="recovery"
    data-testid="recovery-manager"
  >
    <h1 class="recovery__title">
      Account recovery
    </h1>
    <p class="recovery__note">
      Each email opens first, and goes only once you send it from there.
    </p>

    <filter-bar
      :active="filtered"
      testid="recovery-filters"
      @clear="clear"
    >
      <search-box
        v-model="search"
        label="Search for a user"
        testid="recovery-search"
      />
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

    <div class="recovery__columns">
      <sort-header
        :direction="direction('name')"
        label="Name"
        testid="recovery-sort-name"
        @sort="sortBy('name')"
      />
      <span>State</span>
      <sort-header
        class="recovery__wide"
        :direction="direction('lastEmail')"
        label="Last recovery email"
        testid="recovery-sort-last-email"
        @sort="sortBy('lastEmail')"
      />
      <span />
    </div>

    <p
      v-if="loaded && shown.length === 0"
      class="recovery__note"
      data-testid="recovery-empty"
    >
      Nobody matches.
    </p>

    <full-list
      :height="listHeight"
      :row-height="56"
      :row-key="(row) => `${row.standing}-${row.user.id}`"
      :rows="shown"
      testid="recovery-list"
    >
      <template #row="{row}">
        <div
          class="recovery__row"
          :data-testid="`recovery-user-row-${row.user.id}`"
        >
          <span class="recovery__who">
            <strong>{{ row.user.fullName }}</strong>
            <span class="recovery__sub">@{{ row.user.username }}</span>
          </span>
          <span
            class="recovery__sub"
            :data-testid="`recovery-state-${row.user.id}`"
          >{{ standingOf(row) }}</span>
          <span
            class="recovery__wide recovery__sub"
            :data-testid="`recovery-last-email-${row.user.id}`"
          >{{ row.lastEmail ? formatDateNoSeconds(row.lastEmail) : "Never" }}</span>
          <recovery-action
            :action="ACTION[row.standing as Standing]"
            :pending-activation="pending[row.user.id] ?? null"
            :user="row.user"
            @done="load"
          />
        </div>
      </template>
    </full-list>
  </div>
</template>

<style scoped>
.recovery {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 2rem 2.4rem 3rem;
}

.recovery__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.recovery__note {
  margin: 0;
  color: var(--color-ash);
}

.recovery__columns,
.recovery__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 12rem 10rem 11rem;
  align-items: center;
  gap: 0.8rem;
}

.recovery__columns {
  padding: 0 0.6rem;
  font-size: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.recovery__row {
  height: 56px;
  padding: 0 0.6rem;
  border-bottom: 1px solid var(--color-hairline);
}

.recovery__who {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.recovery__who strong,
.recovery__sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recovery__sub {
  font-size: 0.8rem;
  color: var(--color-ash);
}

@media (max-width: 839px) {
  .recovery {
    padding: 1.2rem 1.1rem 2rem;
  }

  .recovery__columns,
  .recovery__row {
    grid-template-columns: minmax(0, 1fr) 6.5rem auto;
    gap: 0.5rem;
  }

  .recovery__wide {
    display: none;
  }
}
</style>
