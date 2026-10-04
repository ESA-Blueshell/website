<script lang="ts" setup>
/* Money only, one contribution period at a time: the strip picks the period, the list says who
   paid, and the selection sends payment emails or marks payments on the bulk task page. */
import {computed, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {useUserSelection} from "@/composables/useUserSelection"
import {
  type ContributionPeriodResponse,
  type PeriodMember,
  type PeriodContributionsView,
  contributionEmailLabels,
  dayName,
  listPeriods,
  readPeriodContributions,
} from "@/domains/contribution"
import {fold} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {feeTypeLabels} from "@/utils/feePreview"
import {formatDay, formatMoment} from "@/utils/timestamps"

defineOptions({name: "ContributionsPage"})

const route = useRoute()
const router = useRouter()

const periods = ref<ContributionPeriodResponse[]>([])
const view = ref<PeriodContributionsView | null>(null)
const search = ref("")
const paid = ref<string | null>(null)
const sortKey = ref<"name" | "lastEmail">("name")
const descending = ref(false)

const paidOptions = [
  {key: "no", label: "Not paid"},
  {key: "yes", label: "Paid"},
]

/** The period in the address, else the newest that has begun, else the newest of all. */
const period = computed<ContributionPeriodResponse | null>(() => {
  const asked = Number(route.params.periodId)
  const today = new Date().toISOString().slice(0, 10)
  return periods.value.find((one) => one.id === asked)
    ?? [...periods.value].reverse().find((one) => one.startDate <= today)
    ?? periods.value.at(-1)
    ?? null
})

const members = computed(() => view.value?.members ?? [])
const shown = computed(() => {
  const words = fold(search.value).split(" ").filter(Boolean)
  const matching = members.value.filter((one) =>
    words.every((word) => fold(`${one.name} ${one.username}`).includes(word))
    && (paid.value === null || one.paid === (paid.value === "yes")))
  // A date sorts as a date; nobody written to yet sorts before anybody who was.
  const sorted = [...matching].sort((a, b) => (sortKey.value === "lastEmail"
    ? (a.lastEmailAt ?? "").localeCompare(b.lastEmailAt ?? "")
    : a.name.localeCompare(b.name)))
  return descending.value ? sorted.reverse() : sorted
})

const paidCount = computed(() => members.value.filter((one) => one.paid).length)
const incassoCount = computed(() => members.value.filter((one) => one.incasso).length)
const filtered = computed(() => search.value !== "" || paid.value !== null)

const {selectedIdsArray, isSelected, toggle, headerState, toggleHeader, selectMany, clear: clearSelection} = useUserSelection(computed(() => shown.value.map((one) => one.userId)))

const euro = (amount: number) => `€ ${amount.toFixed(2)}`

const COLUMNS: TableColumn[] = [
  {key: "name", label: "Member", sortable: true, wrap: true, testid: "contribution-sort-name"},
  {key: "fee", label: "Fee type", wrap: true},
  {key: "incasso", label: "Incasso"},
  {key: "paid", label: "Paid"},
  {key: "lastEmail", label: "Last payment email", sortable: true, wrap: true, testid: "contribution-sort-last-email"},
]

const RUN_COLUMNS: TableColumn[] = [
  {key: "sent", label: "Sent"},
  {key: "what", label: "What", wrap: true},
  {key: "members", label: "Members"},
  {key: "total", label: "Total"},
  {key: "state", label: "State"},
]

const today = new Date().toISOString().slice(0, 10)
/** "2026-2027", or the one year a period starts and ends in. */
const periodName = (one: ContributionPeriodResponse) => {
  const [from, until] = [one.startDate.slice(0, 4), one.endDate.slice(0, 4)]
  return from === until ? from : `${from}-${until}`
}
const isCurrent = (one: ContributionPeriodResponse) => one.startDate <= today && today <= one.endDate

const facts = computed(() => (period.value
  ? [
      {label: "Full-year fee", value: euro(period.value.fullYearFee)},
      {label: "Half-year fee", value: euro(period.value.halfYearFee)},
      {label: "Alumni fee", value: euro(period.value.alumniFee)},
      {label: "Half-year cutoff", value: formatDay(period.value.halfYearCutoffDate)},
      {label: "Paid", value: `${paidCount.value} of ${members.value.length}`, testid: "contribution-paid-count"},
      {label: "On incasso", value: String(incassoCount.value), testid: "contribution-incasso-count"},
    ]
  : []))

interface RunRow {
  key: string
  testid: string
  sent: string
  what: string
  members: number
  total: string
  waiting: boolean
  state: string
  to: string
}

/** What went out this period, incassos and payment emails in one list, newest first. */
const runs = computed<RunRow[]>(() => {
  const incassos = (view.value?.incassoRuns ?? []).map((run): RunRow => ({
    key: `incasso-${run.id}`,
    testid: `contribution-incasso-${run.id}`,
    sent: run.collectionDate,
    what: `Incasso, collected ${dayName(run.collectionDate)}`,
    members: run.collections,
    total: euro(run.total),
    waiting: !run.submittedAt,
    state: run.submittedAt ? `Submitted to ING ${dayName(run.submittedAt.slice(0, 10))}` : "Waiting for upload to ING",
    to: `/management/contributions/${period.value?.id}/incasso/${run.id}`,
  }))
  const emails = (view.value?.runs ?? []).map((run): RunRow => ({
    key: `${run.kind}-${run.sentAt}`,
    testid: `contribution-run-${run.kind}-${run.sentAt}`,
    sent: run.sentAt,
    what: contributionEmailLabels[run.kind],
    members: run.recipients,
    total: "·",
    waiting: false,
    state: "Sent",
    to: "/management/mail/sent",
  }))
  return [...incassos, ...emails].sort((a, b) => b.sent.localeCompare(a.sent))
})

const sortBy = (key: string) => {
  // A date starts newest first; a name starts at the top of the alphabet.
  descending.value = sortKey.value === key ? !descending.value : key === "lastEmail"
  sortKey.value = key as "name" | "lastEmail"
}

const clear = () => {
  search.value = ""
  paid.value = null
}

const loadPeriods = async () => {
  try {
    periods.value = [...await listPeriods()].sort((a, b) => a.startDate.localeCompare(b.startDate))
  } catch (error) {
    $handleNetworkError(error)
  }
}

const loadView = async () => {
  view.value = period.value ? await readPeriodContributions(period.value.id) : null
}

/** Reminders for members paying by transfer have a task page of their own. */
const sendReminders = () => {
  if (!period.value) return
  void router.push({path: `/management/contributions/${period.value.id}/reminders`, query: {ids: selectedIdsArray.value.join(",")}})
}

/** Marking payments happens on the bulk task page, which comes back here once done. */
const markPayments = (action: "paid" | "unpaid", ids: number[] = selectedIdsArray.value) => {
  if (!period.value) return
  void router.push({
    path: `/management/users/bulk/${action}`,
    query: {ids: ids.join(","), period: String(period.value.id), back: `/management/contributions/${period.value.id}`},
  })
}

const feeName = (one: PeriodMember) => (one.feeType && one.fee != null ? feeTypeLabels[one.feeType] : "Owes nothing")
const lastEmail = (one: PeriodMember) => (one.lastEmailAt && one.lastEmailKind ? formatMoment(one.lastEmailAt) : "None yet")

watch(() => period.value?.id, () => {
  clearSelection()
  void loadView()
})
void loadPeriods()
</script>

<template>
  <management-page
    eyebrow="Money"
    testid="contributions-page"
    title="Contributions"
  >
    <template #lede>
      Who owes what for a contribution period, who paid and who was asked. People and their accounts live in Users.
    </template>
    <template
      v-if="period"
      #actions
    >
      <cut-button
        :href="`/management/contributions/periods/${period.id}`"
        testid="contribution-period-edit"
      >
        Edit this period
      </cut-button>
    </template>

    <nav
      aria-label="Contribution periods"
      class="money__strip"
      data-testid="contribution-periods"
    >
      <router-link
        v-for="one in periods"
        :key="one.id"
        :aria-current="period?.id === one.id ? 'page' : undefined"
        class="money__tile"
        :class="{'money__tile--on': period?.id === one.id}"
        :data-testid="`contribution-period-${one.id}`"
        :to="`/management/contributions/${one.id}`"
      >
        <span>
          <span class="money__period">{{ periodName(one) }}</span>
          <span class="money__dates">{{ formatDay(one.startDate) }} to {{ formatDay(one.endDate) }}</span>
        </span>
        <span
          v-if="isCurrent(one)"
          class="money__current"
        >Current</span>
      </router-link>
      <router-link
        class="money__tile money__tile--new"
        data-testid="contribution-period-new"
        to="/management/contributions/periods/new"
      >
        <svg
          aria-hidden="true"
          fill="none"
          viewBox="0 0 12 12"
        >
          <path
            d="M6 1v10M1 6h10"
            stroke="currentColor"
            stroke-width="1.6"
          />
        </svg>
        New period
      </router-link>
    </nav>

    <p
      v-if="!period"
      class="money__note"
      data-testid="contributions-no-period"
    >
      There is no contribution period yet.
    </p>

    <template v-else>
      <fact-list
        class="money__facts"
        :columns="6"
        data-testid="contribution-period-facts"
        :facts="facts"
      />

      <management-table
        :columns="COLUMNS"
        :descending="descending"
        :row-key="(one) => one.userId"
        :row-testid="(one) => `contribution-row-${one.userId}`"
        :rows="shown"
        :sort-key="sortKey"
        testid="contribution-list"
        :to="(one) => `/management/users/${one.userId}/contributions`"
        :header-state="headerState"
        :selected-count="selectedIdsArray.length"
        :total="members.length"
        @clear-selection="clearSelection"
        @select-all="selectMany(members.map((one) => one.userId))"
        @toggle-shown="toggleHeader"
        @sort="sortBy"
      >
        <template #count>
          <span><b>{{ shown.length }}</b> of {{ members.length }} members</span>
        </template>
        <template #filters>
          <filter-bar
            :active="filtered"
            testid="contribution-filters"
            @clear="clear"
          >
            <filter-picker
              v-model="paid"
              label="Paid"
              :options="paidOptions"
              testid="contribution-filter-paid"
            />
          </filter-bar>
        </template>
        <template #search>
          <search-box
            v-model="search"
            label="Search for a user"
            testid="contribution-search"
          />
        </template>
        <template
          v-if="view"
          #empty
        >
          <span data-testid="contributions-empty">Nobody matches.</span>
        </template>
        <template #check="{row}">
          <row-check
            :checked="isSelected(row.userId)"
            :label="`Select ${row.name}`"
            :testid="`contribution-checkbox-${row.userId}`"
            @toggle="toggle(row.userId)"
          />
        </template>
        <template #name="{row}">
          <router-link
            class="mg-name"
            :to="`/management/users/${row.userId}/contributions`"
          >
            {{ row.name }}
          </router-link>
          <span class="mg-sub">{{ row.username }}</span>
        </template>
        <template #fee="{row}">
          {{ feeName(row) }}
          <span
            v-if="row.fee != null && row.feeType"
            class="mg-sub"
          >{{ euro(row.fee) }}</span>
        </template>
        <template #incasso="{row}">
          <span :class="{'mg-quiet': !row.incasso}">{{ row.incasso ? "On incasso" : "No" }}</span>
        </template>
        <template #paid="{row}">
          <state-mark
            :kind="row.paid ? 'in-step' : 'extra'"
            :testid="`contribution-paid-${row.userId}`"
          >
            {{ row.paid ? `Paid${row.paidAt ? ` ${formatDay(row.paidAt)}` : ""}` : "Not paid" }}
          </state-mark>
        </template>
        <template #lastEmail="{row}">
          <span :data-testid="`contribution-last-email-${row.userId}`">
            <span :class="{'mg-quiet': !row.lastEmailAt}">{{ lastEmail(row) }}</span>
            <span
              v-if="row.lastEmailAt && row.lastEmailKind"
              class="mg-sub"
            >{{ contributionEmailLabels[row.lastEmailKind] }}</span>
          </span>
        </template>
        <template #acts="{row}">
          <mini-button
            :testid="`contribution-mark-${row.userId}`"
            @click="markPayments(row.paid ? 'unpaid' : 'paid', [row.userId])"
          >
            {{ row.paid ? "Withdraw" : "Mark paid" }}
          </mini-button>
        </template>
        <template #phone="{row}">
          <management-row
            :meta="`${feeName(row)}${row.incasso ? ' · incasso' : ''}`"
            :name="row.name"
            :testid="`contribution-row-${row.userId}`"
            :to="`/management/users/${row.userId}/contributions`"
          >
            <template #check>
              <row-check
                :checked="isSelected(row.userId)"
                :label="`Select ${row.name}`"
                :testid="`contribution-checkbox-${row.userId}`"
                @toggle="toggle(row.userId)"
              />
            </template>
            <state-mark
              :kind="row.paid ? 'in-step' : 'extra'"
              :testid="`contribution-paid-${row.userId}`"
            >
              {{ row.paid ? `Paid${row.paidAt ? ` ${formatDay(row.paidAt)}` : ""}` : "Not paid" }}
            </state-mark>
          </management-row>
        </template>
      </management-table>

      <selection-bar
        :count="selectedIdsArray.length"
        testid="contribution-selection"
        @clear="clearSelection"
      >
        <cut-button
          small
          testid="bulk-action-send-payment-reminders"
          tone="solid"
          @click="sendReminders"
        >
          Send payment reminders
        </cut-button>
        <cut-button
          small
          testid="bulk-action-mark-paid"
          @click="markPayments('paid')"
        >
          Mark paid
        </cut-button>
        <cut-button
          small
          testid="bulk-action-mark-unpaid"
          @click="markPayments('unpaid')"
        >
          Mark unpaid
        </cut-button>
      </selection-bar>

      <list-head title="Reminders and incassos this period">
        <cut-button
          :href="`/management/contributions/${period.id}/incasso`"
          small
          testid="contribution-incasso-run"
        >
          Run an incasso
        </cut-button>
      </list-head>
      <p
        v-if="runs.length === 0"
        class="money__note"
        data-testid="contribution-runs-empty"
      >
        No payment emails or incassos have gone out for this period yet.
      </p>
      <management-table
        v-else
        :columns="RUN_COLUMNS"
        :row-key="(run) => run.key"
        :row-testid="(run) => run.testid"
        :rows="runs"
        testid="contribution-runs"
      >
        <template #sent="{row}">
          {{ formatDay(row.sent) }}
        </template>
        <template #what="{row}">
          {{ row.what }}
        </template>
        <template #members="{row}">
          {{ row.members }}
        </template>
        <template #total="{row}">
          <span :class="{'mg-quiet': row.total === '·'}">{{ row.total }}</span>
        </template>
        <template #state="{row}">
          <state-mark :kind="row.waiting ? 'extra' : 'in-step'">
            {{ row.state }}
          </state-mark>
        </template>
        <template #acts="{row}">
          <mini-button :to="row.to">
            {{ row.to.includes("/incasso/") ? "Open" : "See the emails" }}
          </mini-button>
        </template>
      </management-table>
    </template>
  </management-page>
</template>

<style scoped>
.money__strip {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  margin-top: 1rem;
}

.money__tile {
  position: relative;
  display: flex;
  flex: 1 1 12rem;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  min-height: 3.9rem;
  padding: 0.7rem 1rem;
  color: var(--color-ash);
  background-color: var(--band-ground);
}

.money__tile:hover {
  color: var(--color-chalk);
}

.money__tile--on {
  color: var(--color-chalk);
  box-shadow: inset 0 -3px 0 var(--color-eyebrow);
}

.money__period {
  display: block;
  font-weight: 600;
}

.money__dates {
  display: block;
  margin-top: 0.15rem;
  font-size: 0.78rem;
  color: var(--color-ash);
}

.money__current {
  padding: 0.26rem 0.55rem;
  border: 1px solid currentColor;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--color-eyebrow);
}

.money__tile--new {
  flex: 0 0 auto;
  justify-content: flex-start;
  gap: 0.6rem;
  font-weight: 600;
  color: var(--color-brand-ink);
}

.money__tile--new svg {
  width: 12px;
  height: 12px;
}

.money__facts {
  padding: 1.4rem 0 0.4rem;
}

.money__count {
  padding: 1rem 0.2rem 0.5rem;
  font-size: 0.85rem;
  color: var(--color-ash);
}

.money__count b {
  color: var(--color-chalk);
}

.money__note {
  margin-top: 1rem;
  color: var(--color-ash);
}
</style>
