<script lang="ts" setup>
/* The association's committees: their seats and what each has on Brevo and Discord. A committee
   missing its role or list stands out. Opening one renders the site's own committee editor inside
   Management. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {type CohortSummary, type SummaryTarget, TargetSystem, fetchCohorts} from "@/domains/cohorts"
import {type Committee, listCommittees} from "@/domains/committees"

defineOptions({name: "CommitteeListPage"})

const committees = ref<Committee[]>([])
const cohorts = ref<CohortSummary[]>([])
const search = ref("")
const loaded = ref(false)
const failed = ref(false)

const SYSTEMS = [
  {system: TargetSystem.DISCORD, none: "No role", mark: (target: SummaryTarget) => `@${target.label}`},
  {system: TargetSystem.BREVO, none: "No list", mark: (target: SummaryTarget) => target.label},
]

const targetsOf = (committee: Committee): SummaryTarget[] =>
  cohorts.value.find((one) => one.definitionKey === `COMMITTEE_MEMBERS:${committee.id}`)?.targets ?? []
const madeOn = (committee: Committee, system: TargetSystem) => targetsOf(committee).find((one) => one.system === system && one.made) ?? null
const lacking = (committee: Committee) => SYSTEMS.filter((one) => !madeOn(committee, one.system)).map((one) => one.none.toLowerCase())

const matches = (committee: Committee) => {
  const needle = search.value.trim().toLowerCase()
  return needle === "" || [committee.name, committee.slug].some((value) => value.toLowerCase().includes(needle))
}
const COLUMNS: TableColumn[] = [
  {key: "name", label: "Committee", wrap: true},
  {key: "seats", label: "Seats"},
  {key: "discord", label: "Discord"},
  {key: "brevo", label: "Brevo"},
  {key: "state", label: "State"},
]

// The committees at work first, the archived ones after them, each by name.
const shown = computed(() => committees.value.filter(matches)
  .sort((a, b) => Number(a.archived) - Number(b.archived) || a.name.localeCompare(b.name)))
const live = computed(() => committees.value.filter((one) => !one.archived))

const facts = computed(() => {
  const people = new Set(live.value.flatMap((one) => (one.members ?? []).map((seat) => seat.userId)))
  return [
    {label: "Committees", value: String(live.value.length), sub: `${committees.value.length - live.value.length} archived`},
    {label: "Seats", value: `${people.size} ${people.size === 1 ? "person" : "people"}`, sub: "Across every committee"},
    {label: "Needs a look", value: String(missing.value.length), sub: "Missing a role or a list"},
  ]
})
const missing = computed(() => committees.value.filter((one) => !one.archived && lacking(one).length > 0))

const seats = (committee: Committee) => {
  const count = committee.members?.length ?? 0
  return `${count} ${count === 1 ? "seat" : "seats"}`
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
      Every committee, its seats, and the role and list its people hold.
    </template>
    <template #actions>
      <cut-button
        href="/management/committees/new"
        testid="committee-list-new"
      >
        Add a committee
      </cut-button>
    </template>

    <fact-list
      class="committees__facts"
      :facts="facts"
    />

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

    <management-table
      :columns="COLUMNS"
      :row-key="(committee) => committee.id"
      :row-testid="(committee) => `committee-row-${committee.id}`"
      :rows="shown"
      testid="committee-list-table"
      :to="(committee) => `/management/committees/${committee.slug}`"
    >
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
      <template #seats="{row}">
        {{ seats(row) }}
      </template>
      <template
        v-for="one in SYSTEMS"
        :key="one.system"
        #[one.system.toLowerCase()]="{row}"
      >
        <span
          v-if="madeOn(row, one.system)"
          :data-testid="`committee-${one.system.toLowerCase()}-${row.id}`"
        >{{ one.mark(madeOn(row, one.system)!) }}</span>
        <state-mark
          v-else
          :kind="row.archived ? 'not-compared' : 'not-created'"
          :testid="`committee-${one.system.toLowerCase()}-${row.id}`"
        >
          {{ one.none }}
        </state-mark>
      </template>
      <template #state="{row}">
        <state-mark :kind="row.archived ? 'not-compared' : 'in-step'">
          {{ row.archived ? "Archived" : "Active" }}
        </state-mark>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${seats(row)} · ${row.slug}`"
          :name="row.name"
          :testid="`committee-row-${row.id}`"
          :to="`/management/committees/${row.slug}`"
        >
          <state-mark :kind="row.archived ? 'not-compared' : lacking(row).length ? 'not-created' : 'in-step'">
            {{ row.archived ? "Archived" : lacking(row).length ? `Missing: ${lacking(row).join(", ")}` : "Active" }}
          </state-mark>
        </management-row>
      </template>
    </management-table>
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
