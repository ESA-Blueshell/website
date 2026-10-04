<script lang="ts" setup>
import {computed, nextTick, onMounted, ref, useTemplateRef, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FoldOut from "@/components/island/FoldOut.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark, {type StateKind} from "@/components/island/StateMark.vue"
import JobRunForm, {type JobPreset} from "@/components/management/JobRunForm.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {loadJob, loadJobPage, loadJobStats, retryJob} from "@/domains/jobs"
import {type Job, type JobStats, JobExecutionCategory, JobExecutionStatus, canRetry, categoryOptions as jobCategoryOptions, effectLabel, errorSummary, payloadChips, previewActorDisplay, previewTitle, retryLabel, statusOptions as jobStatusOptions, statusTitle, successRate as rateOf, titleCase, triggerLabel} from "@/domains/jobs"
import {usePagedTable, type PageQuery} from "@/composables/usePagedTable"
import store from "@/plugins/store"
import {attemptsLabel} from "@/utils/jobAttempts"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "JobManagerPage"})

const route = useRoute()
const router = useRouter()
const PAGE_SIZE = 50

const stats = ref<JobStats | null>(null)
const selectedCategory = ref<string | null>(null)
// An alert links here with the status it is about.
const selectedStatus = ref<string | null>(typeof route.query.status === "string" ? route.query.status : null)
const hideSkipped = ref(false)
const runOpen = ref(false)
const preset = ref<JobPreset | null>(null)
const runPanel = useTemplateRef<HTMLElement>("runPanel")

const loadStats = async () => {
  stats.value = await loadJobStats()
}

// Stats ride along with the rows so the panel and the table describe the same moment, and are
// not awaited: the panel is supplementary and a slow count must not hold the table back.
const loadPage = (query: PageQuery) => {
  void loadStats()
  return loadJobPage(query, {
    ...(selectedCategory.value ? {category: selectedCategory.value as JobExecutionCategory} : {}),
    ...(selectedStatus.value ? {status: selectedStatus.value as JobExecutionStatus} : {}),
    hideSkipped: hideSkipped.value,
  })
}

const table = usePagedTable<Job>(loadPage, {pageSize: PAGE_SIZE})
const {
  rows: executions,
  loading,
  search: searchQuery,
  pageRangeLabel,
  refresh,
  more,
  resetToFirstPage,
} = table

watch([selectedCategory, selectedStatus, hideSkipped], () => {
  resetToFirstPage()
})

/** A job just queued belongs at the top, which is the first page with the filters unchanged. */
const onJobQueued = () => {
  resetToFirstPage()
}

const filtered = computed(() => (searchQuery.value ?? "") !== "" || selectedCategory.value !== null || selectedStatus.value !== null)

const clearFilters = () => {
  searchQuery.value = ""
  selectedCategory.value = null
  selectedStatus.value = null
}

/** Opens Run a job filled in from a job already run; the list under it stays where it was. */
const runAgain = async (execution: Pick<Job, "jobType" | "payload">) => {
  if (!execution.jobType) return
  preset.value = {type: execution.jobType, payload: {...execution.payload}}
  runOpen.value = true
  await nextTick()
  runPanel.value?.scrollIntoView?.({block: "start"})
}

// Read once: both lists come from the generated enums, which do not change while the page is open.
const categoryOptions = jobCategoryOptions()
const statusOptions = jobStatusOptions()

const successRate = computed(() => rateOf(stats.value))

const COLUMNS: TableColumn[] = [
  {key: "queued", label: "Queued"},
  {key: "job", label: "Job", wrap: true},
  {key: "kind", label: "Kind"},
  {key: "by", label: "Started by", wrap: true},
  {key: "status", label: "Status", wrap: true},
  {key: "attempts", label: "Attempts"},
]

const STATUS_MARKS: Record<JobExecutionStatus, StateKind> = {
  [JobExecutionStatus.QUEUED]: "missing",
  [JobExecutionStatus.RUNNING]: "missing",
  [JobExecutionStatus.SUCCESS]: "in-step",
  [JobExecutionStatus.SKIPPED]: "not-compared",
  [JobExecutionStatus.FAILED]: "extra",
  [JobExecutionStatus.DEAD]: "not-created",
}

const facts = computed(() => {
  const read = stats.value
  if (!read) return []
  return [
    {label: "Total", value: String(read.totalCount), testid: "job-stats-total"},
    {label: "Succeeded", value: String(read.successCount), sub: read.totalCount > 0 ? `${successRate.value}%` : "", testid: "job-stats-success"},
    {label: "Failed", value: String(read.failedCount), tone: read.failedCount > 0 ? "warning" as const : undefined, testid: "job-stats-failed"},
    {label: "Dead", value: String(read.deadCount), tone: read.deadCount > 0 ? "danger" as const : undefined, testid: "job-stats-dead"},
    {label: "Queued", value: String(read.queuedCount), testid: "job-stats-queued"},
    {label: "Running", value: String(read.runningCount), testid: "job-stats-running"},
  ]
})

const sinceStartup = computed(() => {
  const read = stats.value
  if (!read) return []
  return [
    {label: "Dead", value: read.deadSinceStartup.toFixed(0), tone: read.deadSinceStartup > 0 ? "danger" as const : undefined},
    {label: "Failed", value: read.failedSinceStartup.toFixed(0), tone: read.failedSinceStartup > 0 ? "warning" as const : undefined},
    {label: "Average run", value: `${read.avgSuccessDurationSeconds.toFixed(2)} s`},
    {label: "Recoveries", value: read.recoveriesSinceStartup.toFixed(0), tone: read.recoveriesSinceStartup > 0 ? "warning" as const : undefined},
  ]
})

const markOf = (execution: Job): StateKind => (execution.status ? STATUS_MARKS[execution.status] : "not-compared")

/** Who or what started a job: the run or the page that queued it, else the person. */
const startedBy = (execution: Job) => (execution.trigger ? triggerLabel(execution) : previewActorDisplay(execution))
const failed = (execution: Job) => execution.status === JobExecutionStatus.FAILED || execution.status === JobExecutionStatus.DEAD

const retry = async (execution: Job) => {
  if (execution.id == null) return
  const retried = await retryJob(execution.id)
  // A refused retry changed nothing, so there is nothing to re-read — and saying nothing is
  // what made pressing Retry look like pressing nothing at all.
  if (!retried.ok) {
    store.commit("setStatusSnackbarMessage", retried.reason)
    return
  }
  await refresh()
}

onMounted(async () => {
  if (!store.getters.isAdmin) {
    await router.replace("/")
    return
  }
  void loadStats()
  await refresh()
  // A job's own page sends Run again here, to the one form that queues jobs.
  const again = Number(route.query.again)
  if (Number.isInteger(again) && again > 0) {
    const job = await loadJob(again)
    if (job) await runAgain(job)
  }
})
</script>

<template>
  <management-page
    eyebrow="System"
    testid="job-manager"
    title="Jobs"
  >
    <template #lede>
      Work the site does in the background: sends, syncs, reconciles and Discord posts.
    </template>
    <template #actions>
      <cut-button
        testid="job-run-open"
        @click="runOpen = !runOpen"
      >
        Run a job
      </cut-button>
      <cut-button
        :disabled="loading"
        testid="job-manager-refresh-btn"
        tone="quiet"
        @click="refresh"
      >
        Refresh
      </cut-button>
    </template>

    <template v-if="stats !== null">
      <fact-list
        class="jobs__facts"
        :columns="6"
        :facts="facts"
      />
      <div data-testid="job-stats-runtime">
        <list-head title="Since start-up" />
        <fact-list
          class="jobs__runtime"
          :columns="4"
          :facts="sinceStartup"
        />
      </div>
    </template>

    <div
      ref="runPanel"
      class="jobs__run"
    >
      <fold-out
        v-model:open="runOpen"
        label="Run a job"
        testid="job-run"
      >
        <job-run-form
          :preset="preset"
          @queued="onJobQueued"
        />
      </fold-out>
    </div>

    <management-table
      :columns="COLUMNS"
      :row-key="(execution) => execution.id ?? 0"
      :row-testid="(execution) => `job-row-${execution.id}`"
      :rows="executions"
      testid="job-manager-table"
      :to="(execution) => `/management/jobs/${execution.id}`"
      @more="more"
    >
      <template #count>
        {{ loading ? "Refreshing" : `Showing ${pageRangeLabel}` }}
      </template>
      <template #filters>
        <check-box
          v-model="hideSkipped"
          class="jobs__skip"
          label="Hide skipped"
          testid="job-filter-hide-skipped"
        />
        <filter-bar
          :active="filtered"
          testid="job-filters"
          @clear="clearFilters"
        >
          <filter-picker
            v-model="selectedStatus"
            label="Status"
            :options="statusOptions"
            testid="job-filter-status"
          />
          <filter-picker
            v-model="selectedCategory"
            label="Kind"
            :options="categoryOptions"
            testid="job-filter-kind"
          />
        </filter-bar>
      </template>
      <template #search>
        <search-box
          label="Search jobs"
          :model-value="searchQuery ?? ''"
          testid="job-filter-search"
          @update:model-value="searchQuery = $event"
        />
      </template>
      <template
        v-if="!loading"
        #empty
      >
        No jobs match. Change the filters or refresh.
      </template>
      <template #queued="{row}">
        {{ formatMoment(row.queuedAt) }}
      </template>
      <template #job="{row}">
        <router-link
          class="jobs__title"
          :data-testid="`job-open-${row.id}`"
          :to="`/management/jobs/${row.id}`"
        >
          {{ previewTitle(row) }}
        </router-link>
        <span
          v-if="payloadChips(row.payload).length"
          class="mg-sub"
          :data-testid="`job-row-payload-${row.id}`"
        >{{ payloadChips(row.payload).map((chip) => `${chip.label}: ${chip.value}`).join(" · ") }}</span>
        <span
          v-if="row.effect"
          class="mg-sub"
          :data-testid="`job-row-effect-${row.id}`"
        >{{ effectLabel(row) }}</span>
      </template>
      <template #kind="{row}">
        {{ titleCase(row.category ?? "other") }}
      </template>
      <template #by="{row}">
        <span :data-testid="`job-row-trigger-${row.id}`">{{ startedBy(row) }}</span>
      </template>
      <template #status="{row}">
        <state-mark :kind="markOf(row)">
          {{ statusTitle(row.status) }}
        </state-mark>
        <span
          v-if="failed(row) && errorSummary(row) !== '-'"
          class="mg-why"
          :data-testid="`job-error-reason-${row.id}`"
        >{{ errorSummary(row) }}</span>
        <span
          v-if="row.skipReason"
          class="mg-why"
          :data-testid="`job-skip-reason-${row.id}`"
        >{{ row.skipReason }}</span>
      </template>
      <template #attempts="{row}">
        {{ attemptsLabel(row.attempts) }}
      </template>
      <template #acts="{row}">
        <mini-button
          v-if="canRetry(row)"
          :testid="`job-retry-btn-${row.id}`"
          @click="retry(row)"
        >
          {{ retryLabel(row) }}
        </mini-button>
        <mini-button
          :testid="`job-run-again-btn-${row.id}`"
          @click="runAgain(row)"
        >
          Run again
        </mini-button>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${titleCase(row.category ?? 'other')} · ${startedBy(row)} · ${formatMoment(row.queuedAt)}`"
          :name="previewTitle(row)"
          :testid="`job-row-${row.id}`"
          :to="`/management/jobs/${row.id}`"
        >
          <state-mark :kind="markOf(row)">
            {{ statusTitle(row.status) }}
          </state-mark>
        </management-row>
      </template>
    </management-table>
  </management-page>
</template>

<style scoped>
.jobs__facts {
  padding: 1.1rem 0 0.4rem;
}

.jobs__runtime {
  padding-bottom: 0.4rem;
}

.jobs__run {
  margin: 1.2rem 0;
}

.jobs__skip {
  align-self: center;
  margin-right: 0.4rem;
}

.jobs__title {
  font-weight: 600;
  color: var(--color-chalk);
}

.jobs__title:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
