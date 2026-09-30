<script lang="ts" setup>
/* Money only, one contribution period at a time: the strip picks the period, the list says who
   paid, and the selection sends payment emails or marks payments on the bulk task page. */
import {computed, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FullList from "@/components/island/FullList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import SortHeader from "@/components/island/SortHeader.vue"
import ContributionPeriodDialog from "@/components/common/modals/ContributionPeriodDialog.vue"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import PaymentEmailWizard from "@/components/common/modals/bulk/paymentEmail/PaymentEmailWizard.vue"
import {useUserSelection} from "@/composables/useUserSelection"
import {
  type ContributionPeriodResponse,
  type PeriodMember,
  type PeriodContributionsView,
  contributionEmailLabels,
  deletePeriod,
  listPeriods,
  readPeriodContributions,
} from "@/domains/contribution"
import {fold} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {feeTypeLabels} from "@/utils/feePreview"
import {formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "ContributionsPage"})

const route = useRoute()
const router = useRouter()

const periods = ref<ContributionPeriodResponse[]>([])
const view = ref<PeriodContributionsView | null>(null)
const search = ref("")
const paid = ref<string | null>(null)
const sortKey = ref<"name" | "lastEmail">("name")
const descending = ref(false)
const editing = ref(false)
const creating = ref(false)
const deleting = ref(false)
const writing = ref(false)

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

const {selectedIdsArray, isSelected, toggle, clear: clearSelection} = useUserSelection(computed(() => shown.value.map((one) => one.userId)))

const euro = (amount: number) => `€ ${amount.toFixed(2)}`
const feeOf = (one: PeriodMember) => (one.feeType && one.fee != null ? `${feeTypeLabels[one.feeType]}, ${euro(one.fee)}` : "Owes nothing")

const sortBy = (key: "name" | "lastEmail") => {
  // A date starts newest first; a name starts at the top of the alphabet.
  descending.value = sortKey.value === key ? !descending.value : key === "lastEmail"
  sortKey.value = key
}

const direction = (key: "name" | "lastEmail") => (sortKey.value === key ? (descending.value ? "desc" : "asc") : null)

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

/** Marking payments happens on the bulk task page, which comes back here once done. */
const markPayments = (action: "paid" | "unpaid") => {
  if (!period.value) return
  void router.push({
    path: `/management/users/bulk/${action}`,
    query: {ids: selectedIdsArray.value.join(","), period: String(period.value.id), back: `/management/contributions/${period.value.id}`},
  })
}

const onPeriodSaved = async (saved?: ContributionPeriodResponse) => {
  await loadPeriods()
  if (saved?.id != null) await router.push(`/management/contributions/${saved.id}`)
}

const confirmDelete = async () => {
  deleting.value = false
  if (!period.value) return
  try {
    await deletePeriod(period.value.id)
    await loadPeriods()
    await router.push("/management/contributions")
  } catch (error) {
    $handleNetworkError(error)
  }
}

const onEmailsDone = async () => {
  clearSelection()
  await loadView()
}

const listHeight = ref(Math.max(320, globalThis.innerHeight - 520))

watch(() => period.value?.id, () => {
  clearSelection()
  void loadView()
})
void loadPeriods()
</script>

<template>
  <div
    class="money"
    data-testid="contributions-page"
  >
    <h1 class="money__title">
      Contributions
    </h1>

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
        {{ one.startDate.slice(0, 4) }}–{{ one.endDate.slice(2, 4) }}
      </router-link>
      <button
        class="money__tile money__tile--new"
        data-testid="contribution-period-new"
        type="button"
        @click="creating = true"
      >
        New period
      </button>
    </nav>

    <p
      v-if="!period"
      class="money__note"
      data-testid="contributions-no-period"
    >
      There is no contribution period yet.
    </p>

    <template v-else>
      <section
        class="money__facts"
        data-testid="contribution-period-facts"
      >
        <dl>
          <div>
            <dt>Period</dt>
            <dd>{{ period.startDate }} to {{ period.endDate }}</dd>
          </div>
          <div>
            <dt>Fees</dt>
            <dd>{{ euro(period.fullYearFee) }} · half year {{ euro(period.halfYearFee) }} · alumni {{ euro(period.alumniFee) }}</dd>
          </div>
          <div>
            <dt>Half-year cutoff</dt>
            <dd>{{ period.halfYearCutoffDate }}</dd>
          </div>
          <div>
            <dt>Paid</dt>
            <dd data-testid="contribution-paid-count">
              {{ paidCount }} of {{ members.length }}
            </dd>
          </div>
          <div>
            <dt>On incasso</dt>
            <dd data-testid="contribution-incasso-count">
              {{ incassoCount }}
            </dd>
          </div>
        </dl>
        <button
          class="money__action"
          data-testid="contribution-period-edit"
          type="button"
          @click="editing = true"
        >
          Edit period
        </button>
      </section>

      <filter-bar
        :active="filtered"
        testid="contribution-filters"
        @clear="clear"
      >
        <search-box
          v-model="search"
          label="Search for a user"
          testid="contribution-search"
        />
        <filter-picker
          v-model="paid"
          label="Paid"
          :options="paidOptions"
          testid="contribution-filter-paid"
        />
      </filter-bar>

      <selection-bar
        :count="selectedIdsArray.length"
        testid="contribution-selection"
        @clear="clearSelection"
      >
        <button
          class="money__action"
          data-testid="bulk-action-send-payment-emails"
          type="button"
          @click="writing = true"
        >
          Send payment emails
        </button>
        <button
          class="money__action"
          data-testid="bulk-action-mark-paid"
          type="button"
          @click="markPayments('paid')"
        >
          Mark paid
        </button>
        <button
          class="money__action"
          data-testid="bulk-action-mark-unpaid"
          type="button"
          @click="markPayments('unpaid')"
        >
          Mark unpaid
        </button>
      </selection-bar>

      <div class="money__columns">
        <span />
        <sort-header
          :direction="direction('name')"
          label="Name"
          testid="contribution-sort-name"
          @sort="sortBy('name')"
        />
        <span class="money__wide">Fee</span>
        <span>Paid</span>
        <sort-header
          class="money__wide"
          :direction="direction('lastEmail')"
          label="Last payment email"
          testid="contribution-sort-last-email"
          @sort="sortBy('lastEmail')"
        />
      </div>

      <p
        v-if="view && shown.length === 0"
        class="money__note"
        data-testid="contributions-empty"
      >
        Nobody matches.
      </p>

      <full-list
        :height="listHeight"
        :row-height="56"
        :row-key="(one) => one.userId"
        :rows="shown"
        testid="contribution-list"
      >
        <template #row="{row}">
          <div
            class="money__row"
            :data-testid="`contribution-row-${row.userId}`"
          >
            <input
              :aria-label="`Select ${row.name}`"
              :checked="isSelected(row.userId)"
              :data-testid="`contribution-checkbox-${row.userId}`"
              type="checkbox"
              @change="toggle(row.userId)"
            >
            <span class="money__who">
              <router-link
                class="money__name"
                :to="`/management/users/${row.userId}/contributions`"
              >{{ row.name }}</router-link>
              <span class="money__sub">@{{ row.username }}{{ row.incasso ? " · incasso" : "" }}</span>
            </span>
            <span class="money__wide money__sub">{{ feeOf(row) }}</span>
            <span
              class="money__sub"
              :data-testid="`contribution-paid-${row.userId}`"
            >{{ row.paid ? `Paid${row.paidAt ? ` ${formatDateNoSeconds(row.paidAt)}` : ""}` : "Not paid" }}</span>
            <span
              class="money__wide money__sub"
              :data-testid="`contribution-last-email-${row.userId}`"
            >{{ row.lastEmailAt && row.lastEmailKind
              ? `${formatDateNoSeconds(row.lastEmailAt)}, ${contributionEmailLabels[row.lastEmailKind].toLowerCase()}`
              : "None yet" }}</span>
          </div>
        </template>
      </full-list>

      <section
        class="money__runs"
        data-testid="contribution-runs"
      >
        <h2>Payment email runs</h2>
        <p
          v-if="(view?.runs ?? []).length === 0"
          class="money__note"
        >
          No payment emails have gone out for this period yet.
        </p>
        <ul>
          <li
            v-for="run in view?.runs ?? []"
            :key="`${run.kind}-${run.sentAt}`"
            :data-testid="`contribution-run-${run.kind}-${run.sentAt}`"
          >
            <span>{{ contributionEmailLabels[run.kind] }}, {{ formatDateNoSeconds(run.sentAt) }}</span>
            <span class="money__sub">Sent to {{ run.recipients }}</span>
            <router-link to="/management/mail/sent">
              See the emails
            </router-link>
          </li>
        </ul>
      </section>
    </template>

    <contribution-period-dialog
      v-if="creating || editing"
      :contribution-period="editing ? period ?? undefined : undefined"
      :show-dialog="true"
      @changed="onPeriodSaved"
      @delete="editing = false; deleting = true"
      @update:show-dialog="creating = false; editing = false"
    />
    <deletion-confirmation-dialog
      v-model="deleting"
      :message="period ? `Are you sure you want to delete the contribution period from ${period.startDate} to ${period.endDate}?` : ''"
      title="Confirm Period Deletion"
      @confirm="confirmDelete"
    />
    <payment-email-wizard
      v-model="writing"
      :period="period"
      :user-ids="selectedIdsArray"
      @done="onEmailsDone"
    />
  </div>
</template>

<style scoped>
.money {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 2rem 2.4rem 3rem;
}

.money__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.money__note {
  margin: 0;
  color: var(--color-ash);
}

.money__strip {
  display: flex;
  gap: 0.4rem;
  overflow-x: auto;
  padding-bottom: 0.3rem;
}

.money__tile {
  flex: 0 0 auto;
  padding: 0.6rem 1rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.9rem;
  color: var(--color-chalk);
  text-decoration: none;
  white-space: nowrap;
  cursor: pointer;
}

.money__tile--on {
  border-color: var(--color-brand);
  box-shadow: inset 0 -3px 0 var(--color-brand);
}

.money__tile--new {
  border-style: dashed;
  color: var(--color-brand);
}

.money__facts {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem;
  background-color: var(--band-ground);
  border: 1px solid var(--color-hairline);
}

.money__facts dl {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem 1.8rem;
  margin: 0;
}

.money__facts dt {
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.money__facts dd {
  margin: 0.2rem 0 0;
}

.money__action {
  padding: 0.4rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.money__columns,
.money__row {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) 12rem 9rem 13rem;
  align-items: center;
  gap: 0.8rem;
}

.money__columns {
  padding: 0 0.6rem;
  font-size: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.money__row {
  height: 56px;
  padding: 0 0.6rem;
  border-bottom: 1px solid var(--color-hairline);
}

.money__who {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.money__name {
  font-weight: 600;
  color: var(--color-chalk);
  text-decoration: none;
}

.money__name,
.money__sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.money__sub {
  font-size: 0.8rem;
  color: var(--color-ash);
}

.money__runs h2 {
  margin: 0 0 0.5rem;
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.money__runs ul {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.money__runs li {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1rem;
}

.money__runs a {
  color: var(--color-brand);
}

@media (max-width: 839px) {
  .money {
    padding: 1.2rem 1.1rem 2rem;
  }

  .money__columns,
  .money__row {
    grid-template-columns: 1.6rem minmax(0, 1fr) 6rem;
    gap: 0.5rem;
  }

  .money__wide {
    display: none;
  }
}
</style>
