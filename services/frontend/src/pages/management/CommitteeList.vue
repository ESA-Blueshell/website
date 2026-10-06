<script lang="ts" setup>
/* The association's committees: their members and what each has on Brevo and Discord. A committee
   missing its role or list stands out, and the ticked ones can be given theirs together. Opening one
   renders the site's own committee editor inside Management. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import BulkAdd from "@/components/management/BulkAdd.vue"
import {DiscordBulkAdd, type DiscordPlaceRequest} from "@/domains/discord"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {useUserSelection} from "@/composables/useUserSelection"
import {type CohortSummary, type SummaryTarget, TargetMark, TargetSystem, createMissingLists, fetchCohorts, readTargetOverview, targetLabel} from "@/domains/cohorts"
import {type Committee, listCommittees, saveCommitteeDiscord} from "@/domains/committees"

defineOptions({name: "CommitteeListPage"})

const committees = ref<Committee[]>([])
const cohorts = ref<CohortSummary[]>([])
const search = ref("")
const loaded = ref(false)
const failed = ref(false)

const SYSTEMS = [
  {system: TargetSystem.DISCORD, none: "No role"},
  {system: TargetSystem.BREVO, none: "No list"},
]

const targetsOf = (committee: Committee): SummaryTarget[] =>
  cohorts.value.find((one) => one.definitionKey === `COMMITTEE_MEMBERS:${committee.id}`)?.targets ?? []
const madeOn = (committee: Committee, system: TargetSystem) => targetsOf(committee).find((one) => one.system === system && one.made) ?? null
const lacking = (committee: Committee) => SYSTEMS.filter((one) => !madeOn(committee, one.system)).map((one) => one.none.toLowerCase())

const matches = (committee: Committee) => {
  const needle = search.value.trim().toLowerCase()
  return needle === "" || [committee.name, committee.slug].some((value) => value.toLowerCase().includes(needle))
}
const COLUMNS: TableColumn<Committee>[] = [
  {key: "name", label: "Committee", wrap: true, sortBy: (committee) => committee.name},
  {key: "members", label: "Members", sortBy: (committee) => committee.members?.length ?? 0},
  {key: "discord", label: "Discord", sortBy: (committee) => targetLabel(targetsOf(committee), TargetSystem.DISCORD)},
  {key: "brevo", label: "Brevo", sortBy: (committee) => targetLabel(targetsOf(committee), TargetSystem.BREVO)},
  {key: "state", label: "State", sortBy: (committee) => (committee.archived ? "Archived" : "Active")},
]

// The committees at work first, the archived ones after them, each by name.
const shown = computed(() => committees.value.filter(matches)
  .sort((a, b) => Number(a.archived) - Number(b.archived) || a.name.localeCompare(b.name)))
const live = computed(() => committees.value.filter((one) => !one.archived))

const facts = computed(() => {
  const people = new Set(live.value.flatMap((one) => (one.members ?? []).map((seat) => seat.userId)))
  return [
    {label: "Committees", value: String(live.value.length), sub: `${committees.value.length - live.value.length} archived`},
    {label: "Members", value: `${people.size} ${people.size === 1 ? "person" : "people"}`, sub: "Across every committee"},
    {label: "Needs a look", value: String(missing.value.length), sub: "Missing a role or a list"},
  ]
})
const missing = computed(() => committees.value.filter((one) => !one.archived && lacking(one).length > 0))

/* Ticked committees are given what they miss together: a role with its private channel, or a list.
   The ones that have it, or are archived, are left out and said so before anything is added. */
const {selectedIdsArray, isSelected, toggle, headerState, toggleHeader, selectMany, clear: clearSelection} =
  useUserSelection(computed(() => shown.value.map((one) => one.id)))
const ticked = computed(() => committees.value.filter((one) => isSelected(one.id)))
const adding = ref<TargetSystem | null>(null)
const listTargets = ref<Map<number, number>>(new Map())

type Addition = {key: number; name: string; note: string; committee: Committee}
const leftOut = (system: TargetSystem) => ticked.value
  .filter((one) => one.archived || madeOn(one, system))
  .map((one) => ({name: one.name, why: one.archived ? "Archived" : system === TargetSystem.DISCORD ? "Has a role already" : "Has a list already"}))
const toAdd = (system: TargetSystem): Addition[] => ticked.value
  .filter((one) => !one.archived && !madeOn(one, system))
  .map((one) => ({key: one.id, name: one.name, note: system === TargetSystem.DISCORD ? `@${one.name} and #${one.slug}` : "A list in the Committees folder", committee: one}))

const cohortOf = (committee: Committee) => cohorts.value.find((one) => one.definitionKey === `COMMITTEE_MEMBERS:${committee.id}`)
const discordRows = computed(() => toAdd(TargetSystem.DISCORD).map(({committee}) => ({key: `COMMITTEE_MEMBERS:${committee.id}`, name: committee.name, channel: committee.slug})))
const saveRole = async (key: string, choice: DiscordPlaceRequest) => {
  const answered = await saveCommitteeDiscord(Number(key.split(":")[1]), choice)
  return answered.ok ? {ok: true as const} : answered
}
const addList = async ({committee}: Addition) => {
  const target = listTargets.value.get(cohortOf(committee)?.id ?? -1)
  if (target == null) return {ok: false as const, reason: "The site expects no list for this committee yet."}
  const answered = await createMissingLists(TargetSystem.BREVO, [target])
  return answered.ok ? {ok: true as const} : answered
}

const startAdding = async (system: TargetSystem) => {
  if (system === TargetSystem.BREVO) {
    const overview = await readTargetOverview(TargetSystem.BREVO)
    listTargets.value = new Map((overview?.missing ?? []).map((one) => [one.cohortId, one.targetId]))
  }
  adding.value = system
}
const added = async () => {
  cohorts.value = await fetchCohorts()
  clearSelection()
}

const members = (committee: Committee) => {
  const count = committee.members?.length ?? 0
  return `${count} ${count === 1 ? "member" : "members"}`
}

onMounted(async () => {
  try {
    const [read, summaries] = await Promise.all([listCommittees(), fetchCohorts()])
    committees.value = read
    cohorts.value = summaries
  } catch {
    failed.value = true
  }
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Content"
    testid="committee-list"
    title="Committees"
  >
    <template #lede>
      Every committee, its members, and the role and list they hold.
    </template>
    <template #actions>
      <cut-button
        href="/management/committees/new"
        testid="committee-list-new"
      >
        Add a committee
      </cut-button>
    </template>

    <p
      v-if="failed"
      class="committees__note"
      data-testid="committee-list-unreadable"
    >
      The committees could not be read. Try again in a moment.
    </p>
    <notice-box
      v-if="missing.length"
      class="committees__notice"
      testid="committee-list-missing"
      :title="`${missing.length} ${missing.length === 1 ? 'committee is' : 'committees are'} missing a role or a list`"
      tone="warning"
    >
      <p>
        {{ missing.map((one) => `${one.name} (${lacking(one).join(", ")})`).join(", ") }}.
      </p>
    </notice-box>

    <fact-list
      class="committees__facts"
      :facts="facts"
    />

    <management-table
      :columns="COLUMNS"
      :row-key="(committee) => committee.id"
      :row-testid="(committee) => `committee-row-${committee.id}`"
      :rows="shown"
      :header-state="headerState"
      :selected-count="selectedIdsArray.length"
      testid="committee-list-table"
      :to="(committee) => `/management/committees/${committee.slug}`"
      :total="committees.length"
      @clear-selection="clearSelection"
      @select-all="selectMany(committees.map((one) => one.id))"
      @toggle-shown="toggleHeader"
    >
      <template #check="{row}">
        <row-check
          :checked="isSelected(row.id)"
          :label="`Select ${row.name}`"
          :testid="`committee-check-${row.id}`"
          @toggle="toggle(row.id)"
        />
      </template>
      <template #count>
        <b>{{ shown.length }}</b> of {{ committees.length }} committees
      </template>
      <template #search>
        <search-box
          v-model="search"
          label="Search committees"
          testid="committee-list-search"
        />
      </template>
      <template
        v-if="loaded && !failed"
        #empty
      >
        <span data-testid="committee-list-empty">No committee matches.</span>
      </template>
      <template #name="{row}">
        <router-link
          class="mg-name"
          :to="`/management/committees/${row.slug}`"
        >
          {{ row.name }}
        </router-link>
        <span class="mg-sub">{{ row.slug }}</span>
      </template>
      <template #members="{row}">
        {{ members(row) }}
      </template>
      <template
        v-for="one in SYSTEMS"
        :key="one.system"
        #[one.system.toLowerCase()]="{row}"
      >
        <target-mark
          :quiet="row.archived"
          :system="one.system"
          :targets="targetsOf(row)"
          :testid="`committee-${one.system.toLowerCase()}-${row.id}`"
        />
      </template>
      <template #state="{row}">
        <state-mark :kind="row.archived ? 'not-compared' : 'in-sync'">
          {{ row.archived ? "Archived" : "Active" }}
        </state-mark>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${members(row)} · ${row.slug}`"
          :name="row.name"
          :testid="`committee-row-${row.id}`"
          :to="`/management/committees/${row.slug}`"
        >
          <state-mark :kind="row.archived ? 'not-compared' : lacking(row).length ? 'not-created' : 'in-sync'">
            {{ row.archived ? "Archived" : lacking(row).length ? `Missing: ${lacking(row).join(", ")}` : "Active" }}
          </state-mark>
        </management-row>
      </template>
    </management-table>

    <selection-bar
      always
      :count="selectedIdsArray.length"
      testid="committee-list-selection"
      @clear="clearSelection"
    >
      <cut-button
        small
        testid="committee-add-roles"
        tone="solid"
        @click="startAdding(TargetSystem.DISCORD)"
      >
        Add Discord role and channel
      </cut-button>
      <cut-button
        small
        testid="committee-add-lists"
        @click="startAdding(TargetSystem.BREVO)"
      >
        Add Brevo list
      </cut-button>
    </selection-bar>

    <bulk-add
      each="a Brevo list"
      :items="adding === TargetSystem.BREVO ? toAdd(adding) : []"
      :noun="['committee', 'committees']"
      :open="adding === TargetSystem.BREVO"
      :run="addList"
      :skipped="adding === TargetSystem.BREVO ? leftOut(adding) : []"
      testid="committee-bulk-add"
      title="Add Brevo lists"
      @done="added"
      @update:open="adding = null"
    />
    <discord-bulk-add
      :noun="['committee', 'committees']"
      :open="adding === TargetSystem.DISCORD"
      :rows="discordRows"
      :save="saveRole"
      :skipped="adding === TargetSystem.DISCORD ? leftOut(adding) : []"
      testid="committee-discord-add"
      title="Add Discord roles and channels"
      @done="added"
      @update:open="adding = null"
    />
  </management-page>
</template>

<style scoped>
.committees__facts {
  padding: 1.1rem 0 1.2rem;
}

.committees__notice {
  margin-bottom: 1.2rem;
}

.committees__note {
  margin-bottom: 1rem;
  color: var(--color-ash);
}
</style>
