<script lang="ts" setup>
/* One target's drift: each person on one side only with why and what can be done, a search, the
   ticked ones resolved together after a plan is confirmed, and the resolutions so far. A person
   with no account on a system that cannot make one is linked from their own page instead. */
import {computed, ref} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import {formatDateNoSeconds} from "@/utils/timestamps"
import type {Cohort, CohortMember, TargetMapping} from "../adapters/cohorts"
import {type DriftAction, useDriftResolution} from "../composables/useDriftResolution"
import {adoptWord, driftRowsOf, driftWords, whyOf} from "../listPage"
import {memberName} from "../reading"

defineOptions({name: "TargetDrift"})

const props = defineProps<{
  cohort: Cohort
  mapping: TargetMapping
  /** Prefixes every testid in the section, so a page's tests read as before. */
  testid: string
  reload: () => Promise<void>
}>()

const search = ref("")
const system = computed(() => props.mapping.system)
const words = computed(() => driftWords(system.value))
const drift = computed(() => driftRowsOf(props.cohort.members, system.value, search.value))
const resolutions = computed(() => props.cohort.resolutions.filter((one) => one.system === system.value))
const adopt = computed(() => adoptWord(props.cohort.type))

const cohortId = computed(() => props.cohort.id)
const mappings = computed(() => [props.mapping])
const resolution = useDriftResolution(cohortId, mappings, () => props.reload())
const {selection, plan, planCount, working} = resolution

const ACTIONS: DriftAction[] = ["push", "adopt", "link", "remove"]
const actionWord = (action: DriftAction) => {
  if (action === "adopt") return adopt.value ?? "Take in"
  return words.value[action]
}
const rowActions = (row: CohortMember): DriftAction[] =>
  row.unreachable ? [] : ACTIONS.filter((action) => (action !== "adopt" || adopt.value !== null) && resolution.canResolve(action, row))
const reachable = computed(() => drift.value.filter((row) => !row.unreachable))
const bulkActions = computed(() =>
  ACTIONS
    .filter((action) => action !== "adopt" || adopt.value !== null)
    .map((action) => ({action, count: resolution.selectedFor(action, reachable.value)}))
    .filter((one) => one.count > 0))
const ticked = (row: CohortMember) => selection.value.has(row.targetMemberId)
const prepareSelected = (action: DriftAction) => resolution.prepare(action, reachable.value.filter(ticked))
const planTitle = computed(() => (plan.value ? `${actionWord(plan.value.action)}: ${planCount.value} ${planCount.value === 1 ? "person" : "people"}` : ""))
const planPeople = computed(() => plan.value?.groups.flatMap((group) => group.people) ?? [])
const proposalFor = (row: CohortMember) => plan.value?.proposals.find((one) => one.externalUserId === row.externalUserId) ?? null
const markOf = (row: CohortMember) => (row.sync === "ONLY_HERE" ? "missing" : "extra")
</script>

<template>
  <h2 class="drift__part">
    Drift
  </h2>
  <search-box
    v-model="search"
    label="Search for a user"
    :testid="`${testid}-search`"
  />
  <p
    v-if="resolution.message.value"
    class="drift__note"
    :data-testid="`${testid}-message`"
    role="status"
  >
    {{ resolution.message.value }}
  </p>
  <p
    v-if="resolution.error.value"
    class="drift__failure"
    :data-testid="`${testid}-error`"
    role="alert"
  >
    {{ resolution.error.value }}
  </p>
  <p
    v-if="drift.length === 0"
    class="drift__note"
    :data-testid="`${testid}-in-step`"
  >
    {{ search ? "Nobody drifting matches." : "Everybody is where they should be." }}
  </p>
  <ul class="drift__rows">
    <li
      v-for="row in drift"
      :key="row.targetMemberId"
      class="drift__row"
      :data-testid="`${testid}-row-${row.targetMemberId}`"
    >
      <check-box
        :disabled="row.unreachable"
        :label="`Select ${memberName(row)}`"
        :model-value="ticked(row)"
        :testid="`${testid}-select-${row.targetMemberId}`"
        @update:model-value="resolution.toggle(row)"
      />
      <span class="drift__who">
        <router-link
          v-if="row.userId != null"
          :to="`/management/users/${row.userId}`"
        >{{ memberName(row) }}</router-link>
        <span v-else>{{ words.nameless }}</span>
        <span class="drift__sub">{{ row.userEmail ?? row.externalLabel ?? "" }}</span>
      </span>
      <state-mark
        v-if="row.unreachable"
        kind="unreachable"
      >
        {{ words.unreachableMark }}
      </state-mark>
      <state-mark
        v-else
        :kind="markOf(row)"
      />
      <span class="drift__sub drift__why">{{ whyOf(row, cohort.label, words) }}</span>
      <span class="drift__row-acts">
        <router-link
          v-if="row.unreachable && row.userId != null"
          class="drift__mini"
          :data-testid="`${testid}-link-account-${row.targetMemberId}`"
          :to="`/management/users/${row.userId}`"
        >
          Link an account
        </router-link>
        <button
          v-for="action in rowActions(row)"
          :key="action"
          class="drift__mini"
          :class="{'drift__mini--danger': action === 'remove'}"
          :data-testid="`${testid}-${action}-${row.targetMemberId}`"
          :disabled="working"
          type="button"
          @click="resolution.prepare(action, [row])"
        >
          {{ actionWord(action) }}
        </button>
      </span>
    </li>
  </ul>
  <selection-bar
    :count="selection.size"
    :testid="`${testid}-selection`"
    @clear="resolution.clear"
  >
    <button
      v-for="one in bulkActions"
      :key="one.action"
      class="drift__action"
      :data-testid="`${testid}-bulk-${one.action}`"
      :disabled="working"
      type="button"
      @click="prepareSelected(one.action)"
    >
      {{ actionWord(one.action) }}: {{ one.count }}
    </button>
  </selection-bar>

  <h2 class="drift__part">
    Resolved
  </h2>
  <table
    v-if="resolutions.length"
    class="drift__log"
    :data-testid="`${testid}-resolved`"
  >
    <thead>
      <tr>
        <th>When</th>
        <th>Who</th>
        <th>What</th>
        <th>Who it concerned</th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="(one, index) in resolutions"
        :key="index"
      >
        <td>{{ formatDateNoSeconds(one.resolvedAt) }}</td>
        <td>{{ one.resolvedByName ?? "The site" }}</td>
        <td>{{ words.resolved[one.action] }}</td>
        <td>{{ one.personName ?? "" }}</td>
      </tr>
    </tbody>
  </table>
  <p
    v-else
    class="drift__note"
  >
    Nothing resolved yet.
  </p>

  <modal-dialog
    :open="plan !== null"
    :testid="`${testid}-plan`"
    :title="planTitle"
    @update:open="(open: boolean) => { if (!open) resolution.cancel() }"
  >
    <ul class="drift__plan">
      <li
        v-for="row in planPeople"
        :key="row.targetMemberId"
      >
        {{ memberName(row) }}
        <template v-if="plan?.action === 'link'">
          : {{ proposalFor(row)?.userFullName ?? words.unclaimed }}
        </template>
      </li>
    </ul>
    <template #footer>
      <button
        class="drift__action drift__action--main"
        :data-testid="`${testid}-plan-confirm`"
        :disabled="working"
        type="button"
        @click="resolution.confirm"
      >
        {{ plan ? actionWord(plan.action) : "" }}
      </button>
    </template>
  </modal-dialog>
</template>

<style scoped>
.drift__part {
  margin: 1rem 0 0;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.drift__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.drift__failure {
  margin: 0;
  color: var(--color-error, #e5484d);
}

.drift__action,
.drift__mini {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  text-decoration: none;
  cursor: pointer;
}

.drift__mini {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
}

/* Red text falls below contrast on the page; the border carries the warning instead. */
.drift__mini--danger {
  border-color: var(--color-error, #e5484d);
}

.drift__action--main {
  align-self: flex-start;
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.drift__action:disabled,
.drift__mini:disabled {
  opacity: 0.45;
  cursor: default;
}

.drift__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.drift__row {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) 6rem minmax(0, 1.4fr) auto;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.drift__who {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.drift__who a {
  color: var(--color-chalk);
  font-weight: 600;
}

.drift__sub {
  display: block;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.drift__why {
  white-space: normal;
}

.drift__row-acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.4rem;
}

.drift__log {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.86rem;
}

.drift__log th,
.drift__log td {
  padding: 0.5rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
  text-align: left;
}

.drift__log th {
  font-weight: 400;
  color: var(--color-ash);
}

.drift__plan {
  margin: 0;
  padding-left: 1.2rem;
}

@media (max-width: 839px) {
  .drift__row {
    grid-template-columns: 2rem minmax(0, 1fr) auto;
  }

  .drift__why,
  .drift__row-acts {
    grid-column: 2 / -1;
  }
}
</style>
