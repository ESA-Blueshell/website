<script lang="ts" setup>
/* The association's committees: their seats and what each has on Brevo and Discord. A committee
   missing its role or list stands out. Opening one renders the site's own committee editor inside
   Management. */
import {computed, onMounted, ref} from "vue"
import FoldOut from "@/components/island/FoldOut.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
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
const shown = computed(() => committees.value.filter(matches).sort((a, b) => a.name.localeCompare(b.name)))
const live = computed(() => shown.value.filter((one) => !one.archived))
const archived = computed(() => shown.value.filter((one) => one.archived))
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
  <div
    class="committees"
    data-testid="committee-list"
  >
    <header class="committees__head">
      <div>
        <p class="committees__eyebrow">
          Content
        </p>
        <h1 class="committees__title">
          Committees
        </h1>
        <p class="committees__note">
          Each committee's seats, and the role and list its people hold.
        </p>
      </div>
      <router-link
        class="committees__action"
        data-testid="committee-list-new"
        to="/management/committees/new"
      >
        Add a committee
      </router-link>
    </header>

    <p
      v-if="failed"
      class="committees__note"
      data-testid="committee-list-unreadable"
    >
      The committees could not be read. Try again in a moment.
    </p>

    <notice-box
      v-if="missing.length"
      testid="committee-list-missing"
      :title="`${missing.length} ${missing.length === 1 ? 'committee is' : 'committees are'} missing a role or a list`"
    >
      <p>
        {{ missing.map((one) => `${one.name} (${lacking(one).join(", ")})`).join(", ") }}.
      </p>
    </notice-box>

    <search-box
      v-model="search"
      label="Search committees"
      testid="committee-list-search"
    />

    <p
      v-if="loaded && !failed && shown.length === 0"
      class="committees__note"
      data-testid="committee-list-empty"
    >
      No committee matches.
    </p>

    <component
      :is="index === 1 ? FoldOut : 'section'"
      v-for="(group, index) in [live, archived]"
      :key="index"
      v-bind="index === 1 ? {label: `Archived · ${group.length}`, testid: 'committee-list-archived'} : {}"
    >
      <ul
        v-if="group.length"
        class="committees__rows"
      >
        <li
          v-for="committee in group"
          :key="committee.id"
          class="committees__row"
          :data-testid="`committee-row-${committee.id}`"
        >
          <span class="committees__name">
            <router-link :to="`/management/committees/${committee.slug}`">{{ committee.name }}</router-link>
          </span>
          <span class="committees__sub">{{ seats(committee) }}</span>
          <template
            v-for="one in SYSTEMS"
            :key="one.system"
          >
            <span
              v-if="madeOn(committee, one.system)"
              class="committees__sub"
              :data-testid="`committee-${one.system.toLowerCase()}-${committee.id}`"
            >{{ one.mark(madeOn(committee, one.system)!) }}</span>
            <state-mark
              v-else
              kind="missing"
              :testid="`committee-${one.system.toLowerCase()}-${committee.id}`"
            >
              {{ one.none }}
            </state-mark>
          </template>
        </li>
      </ul>
    </component>
  </div>
</template>

<style scoped>
.committees {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.committees__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.committees__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.committees__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.committees__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.committees__action {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  font-size: 0.86rem;
  color: var(--color-chalk);
  text-decoration: none;
}

.committees__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.committees__row {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) 6rem minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.committees__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.committees__name a {
  color: var(--color-chalk);
}

.committees__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 839px) {
  .committees {
    padding: 1.2rem 1.1rem 2rem;
  }

  .committees__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
