<script lang="ts" setup>
/* One target's drift: each person on one side only with why and what can be done, a search, the
   ticked ones resolved together after a plan is confirmed, and the resolutions so far. A person
   with no account on a system that cannot make one is linked from their own page instead. */
import {computed, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import PersonLink from "@/components/management/PersonLink.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {formatMoment} from "@/utils/timestamps"
import {type Cohort, type CohortMember, type TargetMapping, TargetSystem} from "../adapters/cohorts"
import {DiscordUser} from "@/domains/discord"
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
  if (action === "adopt") return adopt.value ?? "Add to the site"
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

const markOf = (row: CohortMember) => (row.sync === "ONLY_HERE" ? "missing" : "extra")
const COLUMNS: TableColumn<CohortMember>[] = [
  {key: "who", label: "Person", wrap: true, sortBy: memberName},
  {key: "state", label: "State", sortBy: (row) => (row.unreachable ? "unreachable" : markOf(row))},
  {key: "why", label: "Why", wrap: true, sortBy: (row) => whyOf(row, props.cohort.label, words.value)},
]
const LOG_COLUMNS: TableColumn<Cohort["resolutions"][number]>[] = [
  {key: "when", label: "When", sortBy: (one) => one.resolvedAt},
  {key: "by", label: "By", sortBy: (one) => one.resolvedByName ?? "The site"},
  {key: "what", label: "What", wrap: true, sortBy: (one) => words.value.resolved[one.action]},
  {key: "who", label: "Who it concerned", sortBy: (one) => one.personName},
]

/** How the people who can be selected stand against the selection; nobody reachable leaves the head unticked. */
const headerState = computed(() => {
  const picked = reachable.value.filter(ticked).length
  if (picked === 0) return "unchecked"
  return picked === reachable.value.length ? "checked" : "indeterminate"
})
/** The head's tick takes everyone reachable, or lets go of them once they are all taken. */
const toggleShown = () => {
  const all = headerState.value === "checked"
  for (const row of reachable.value) if (ticked(row) === all) resolution.toggle(row)
}
const prepareSelected = (action: DriftAction) => resolution.prepare(action, reachable.value.filter(ticked))
const planTitle = computed(() => (plan.value ? `${actionWord(plan.value.action)}: ${planCount.value} ${planCount.value === 1 ? "person" : "people"}` : ""))
const planPeople = computed(() => plan.value?.groups.flatMap((group) => group.people) ?? [])
const proposalFor = (row: CohortMember) => plan.value?.proposals.find((one) => one.externalUserId === row.externalUserId) ?? null
</script>

<template>
  <list-head title="Drift" />
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
  <management-table
    :columns="COLUMNS"
    :header-state="headerState"
    :row-key="(row) => row.targetMemberId"
    :row-testid="(row) => `${testid}-row-${row.targetMemberId}`"
    :rows="drift"
    :testid="`${testid}-drift`"
    @toggle-shown="toggleShown"
  >
    <template #count>
      {{ drift.length }} {{ drift.length === 1 ? "person differs" : "people differ" }}
    </template>
    <template #search>
      <search-box
        v-model="search"
        label="Search for a user"
        :testid="`${testid}-search`"
      />
    </template>
    <template #empty>
      <span :data-testid="`${testid}-in-sync`">{{ search ? "Nobody who differs matches." : "Everybody is where they should be." }}</span>
    </template>
    <template #check="{row}">
      <row-check
        v-if="!row.unreachable"
        :checked="ticked(row)"
        :label="`Select ${memberName(row)}`"
        :testid="`${testid}-select-${row.targetMemberId}`"
        @toggle="resolution.toggle(row)"
      />
    </template>
    <template #who="{row}">
      <router-link
        v-if="row.userId != null"
        class="mg-name"
        :to="`/management/users/${row.userId}`"
      >
        {{ memberName(row) }}
      </router-link>
      <span
        v-else
        class="mg-name"
      >{{ words.nameless }}</span>
      <span
        v-if="system === TargetSystem.DISCORD && row.externalLabel"
        class="mg-sub"
      >
        <discord-user
          :id="row.externalUserId"
          :name="row.externalLabel"
          size="sm"
        />
      </span>
      <span
        v-else
        class="mg-sub"
      >{{ row.userEmail ?? row.externalLabel ?? "" }}</span>
    </template>
    <template #state="{row}">
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
    </template>
    <template #why="{row}">
      <span class="mg-quiet">{{ whyOf(row, cohort.label, words) }}</span>
    </template>
    <template #acts="{row}">
      <mini-button
        v-if="row.unreachable && row.userId != null"
        :testid="`${testid}-link-account-${row.targetMemberId}`"
        :to="`/management/users/${row.userId}`"
      >
        Link an account
      </mini-button>
      <mini-button
        v-for="action in rowActions(row)"
        :key="action"
        :disabled="working"
        :testid="`${testid}-${action}-${row.targetMemberId}`"
        :tone="action === 'remove' ? 'danger' : 'plain'"
        @click="resolution.prepare(action, [row])"
      >
        {{ actionWord(action) }}
      </mini-button>
    </template>
    <template #phone="{row}">
      <management-row
        :meta="whyOf(row, cohort.label, words)"
        :name="row.userId != null ? memberName(row) : words.nameless"
        :testid="`${testid}-row-${row.targetMemberId}`"
      >
        <template
          v-if="!row.unreachable"
          #check
        >
          <row-check
            :checked="ticked(row)"
            :label="`Select ${memberName(row)}`"
            :testid="`${testid}-select-${row.targetMemberId}`"
            @toggle="resolution.toggle(row)"
          />
        </template>
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
      </management-row>
    </template>
  </management-table>

  <selection-bar
    :count="selection.size"
    :testid="`${testid}-selection`"
    @clear="resolution.clear"
  >
    <cut-button
      v-for="one in bulkActions"
      :key="one.action"
      :disabled="working"
      small
      :testid="`${testid}-bulk-${one.action}`"
      @click="prepareSelected(one.action)"
    >
      {{ actionWord(one.action) }}: {{ one.count }}
    </cut-button>
  </selection-bar>

  <list-head title="Resolved" />
  <management-table
    v-if="resolutions.length"
    :columns="LOG_COLUMNS"
    :row-key="(one) => `${one.resolvedAt}-${one.action}-${one.personName}`"
    :rows="resolutions"
    :testid="`${testid}-resolved`"
  >
    <template #when="{row}">
      {{ formatMoment(row.resolvedAt) }}
    </template>
    <template #by="{row}">
      <person-link
        :name="row.resolvedByName ?? 'The site'"
        :user-id="row.resolvedById"
      />
    </template>
    <template #what="{row}">
      {{ words.resolved[row.action] }}
    </template>
    <template #who="{row}">
      <person-link
        :name="row.personName"
        :user-id="row.userId"
      />
    </template>
  </management-table>
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
      <cut-button
        :disabled="working"
        :testid="`${testid}-plan-confirm`"
        tone="solid"
        @click="resolution.confirm"
      >
        {{ plan ? actionWord(plan.action) : "" }}
      </cut-button>
    </template>
  </modal-dialog>
</template>

<style scoped>
.drift__note {
  margin-bottom: 0.8rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.drift__failure {
  margin-bottom: 0.8rem;
  font-size: 0.9rem;
  color: var(--color-danger);
}

.drift__plan {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.92rem;
}
</style>
