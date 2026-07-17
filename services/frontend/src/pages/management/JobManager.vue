<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import JobTriggerDialog from "@/components/common/modals/JobTriggerDialog.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {JobExecutionCategory, JobExecutionStatus, type JobExecution, type JobStatsDto, getStats, list, retry as retryJob} from "@/services/api"
import store from "@/plugins/store"
import {attemptsLabel} from "@/utils/jobAttempts"
import {jobCatalogEntry} from "@/utils/jobCatalog"

defineOptions({name: "JobManagerPage"})

const router = useRouter()
const PAGE_SIZE = 50

type JobRelatedEntity = {
  type?: string
  id?: number
  label?: string
}

type JobExecutionView = JobExecution & {
  category?: JobExecutionCategory
  stackTrace?: string | null
  initiatedByDisplay?: string
  initiatedByUsername?: string | null
  initiatedByFullName?: string | null
  relatedEntities?: JobRelatedEntity[]
}

const summarizeExecution = (execution: JobExecutionView): string => {
  const title = jobCatalogEntry(execution.jobType ?? "").title
  const primary = execution.relatedEntities?.[0]?.label
  return primary ? `${title} — ${primary}` : title
}

const titleCaseToken = (value: string): string =>
  value.charAt(0).toUpperCase() + value.slice(1).toLowerCase()

const humanizeFieldName = (name: string): string =>
  name
    .replace(/([A-Z])/g, " $1")
    .replace(/[._-]+/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map(titleCaseToken)
    .join(" ")

const formatPayloadValue = (value: unknown): string => {
  if (value == null) return "—"
  if (typeof value === "string") return value
  if (typeof value === "number" || typeof value === "boolean") return String(value)
  try { return JSON.stringify(value) } catch { return String(value) }
}

type PayloadChip = { key: string; label: string; value: string }

/**
 * Renders the raw payload map as `{label, value}` chips. Three reasons
 * a key gets dropped:
 *  - It's already surfaced as a resolved related-entity chip (userId,
 *    eventId, periodId, contributionPeriodId, cohortId, eventSignUpId).
 *  - It's a marker / placeholder that has no useful display value
 *    ("unused" sentinels on zero-argument payloads, internal flags,
 *    empty objects).
 *  - It carries secrets / opaque blobs that should never appear in the
 *    admin UI (any "token"-shaped field, full HTML bodies, raw JSON
 *    blobs, etc.).
 */
const SUPPRESSED_PAYLOAD_KEYS = new Set([
  "userid",
  "eventid",
  "eventsignupid",
  "periodid",
  "contributionperiodid",
  "cohortid",
  "unused",
])

const isSensitiveKey = (key: string): boolean => {
  const k = key.toLowerCase()
  return k.includes("token") || k.includes("secret") || k.includes("password") || k.includes("apikey") || k === "key"
}

const isUninterestingValue = (value: unknown): boolean => {
  if (value == null) return true
  if (typeof value === "string") return value.trim() === ""
  if (typeof value === "object") {
    // Empty objects (e.g. `{}` from Unit-payload jobs) carry no info.
    return Object.keys(value as Record<string, unknown>).length === 0
  }
  return false
}

const payloadChips = (execution: JobExecutionView): PayloadChip[] => {
  const payload = execution.payload
  if (!payload || typeof payload !== "object") return []
  return Object.entries(payload)
    .filter(([key, value]) => {
      if (SUPPRESSED_PAYLOAD_KEYS.has(key.toLowerCase())) return false
      if (isSensitiveKey(key)) return false
      if (isUninterestingValue(value)) return false
      return true
    })
    .map(([key, value]) => ({
      key,
      label: humanizeFieldName(key),
      value: formatPayloadValue(value),
    }))
}

type JobPage = {
  content?: JobExecutionView[]
  page?: {
    number?: number
    size?: number
    totalElements?: number
    totalPages?: number
  }
}

type JobListQuery = {
  page: number
  size: number
  sort: string[]
  category?: JobExecutionCategory
  status?: JobExecution["status"]
  search?: string
}

const executions = ref<JobExecutionView[]>([])
const loading = ref<boolean>(false)
const stats = ref<JobStatsDto | null>(null)
const selectedCategory = ref<JobExecutionCategory | "all">("all")
const selectedStatus = ref<string>("all")
const searchQuery = ref<string | null>("")
const expandedRows = ref<number[]>([])
const page = ref<number>(1)
const totalPages = ref<number>(1)
const totalElements = ref<number>(0)
let searchDebounceHandle: ReturnType<typeof setTimeout> | undefined

const clearSearchDebounce = () => {
  if (searchDebounceHandle) {
    clearTimeout(searchDebounceHandle)
    searchDebounceHandle = undefined
  }
}

const successRate = computed(() => {
  if (!stats.value || stats.value.totalCount === 0) return 0
  return Math.round(stats.value.successCount / stats.value.totalCount * 100)
})

/**
 * Counts come from the dedicated stats endpoint so the chips reflect
 * DB totals, not whichever page is currently loaded. While stats are
 * loading we fall back to a zero map so the chip row stays mounted.
 */
const statusCounts = computed<Record<JobExecutionStatus, number>>(() => ({
  [JobExecutionStatus.QUEUED]: stats.value?.queuedCount ?? 0,
  [JobExecutionStatus.RUNNING]: stats.value?.runningCount ?? 0,
  [JobExecutionStatus.SUCCESS]: stats.value?.successCount ?? 0,
  [JobExecutionStatus.FAILED]: stats.value?.failedCount ?? 0,
  [JobExecutionStatus.DEAD]: stats.value?.deadCount ?? 0,
}))

const categoryOptions = computed(() => {
  return [
    {title: "All categories", value: "all"},
    ...Object.values(JobExecutionCategory).map((value) => ({title: titleCase(value), value})),
  ]
})

const statusOptions = computed(() => {
  return [
    {title: "All statuses", value: "all"},
    ...Object.values(JobExecutionStatus).map((value) => ({title: titleCase(value), value})),
  ]
})

const pageRangeLabel = computed<string>(() => {
  if (totalElements.value === 0 || executions.value.length === 0) return `0 of ${totalElements.value}`
  const start = (page.value - 1) * PAGE_SIZE + 1
  const end = Math.min(start + executions.value.length - 1, totalElements.value)
  return `${start}-${end} of ${totalElements.value}`
})

const errorSummary = (execution: JobExecutionView): string => {
  return execution.errorMessage ?? execution.errorReason ?? "-"
}

const hasStackTrace = (execution: JobExecutionView): boolean => {
  return !!execution.stackTrace || looksLikeStackTrace(execution.errorReason)
}

const stackTrace = (execution: JobExecutionView): string => {
  if (execution.stackTrace) return execution.stackTrace
  if (execution.errorReason && looksLikeStackTrace(execution.errorReason)) return execution.errorReason
  return ""
}

const actorDisplay = (execution: JobExecutionView): string => {
  if (execution.initiatedByDisplay) return execution.initiatedByDisplay
  if (execution.initiatedByFullName && execution.initiatedByUsername) {
    return `${execution.initiatedByFullName} (@${execution.initiatedByUsername})`
  }
  if (execution.initiatedByType === "SYSTEM") return "System"
  if (execution.initiatedByUserId != null) return `User #${execution.initiatedByUserId}`
  return "System"
}

const previewActorDisplay = (execution: JobExecutionView): string => {
  if (execution.initiatedByFullName?.trim()) return execution.initiatedByFullName
  if (execution.initiatedByDisplay?.trim()) {
    return execution.initiatedByDisplay.replace(/\s*\(@[^)]+\)\s*$/, "")
  }
  if (execution.initiatedByType === "SYSTEM") return "System"
  if (execution.initiatedByUserId != null) return `User #${execution.initiatedByUserId}`
  return "System"
}

const previewTitle = (execution: JobExecutionView): string => {
  const summary = summarizeExecution(execution).trim()
  if (summary) return summary
  return `${titleCase(execution.category ?? "job")} job`
}

const jobDescription = (execution: JobExecutionView): string =>
  jobCatalogEntry(execution.jobType ?? "").description

const isExpanded = (execution: JobExecutionView): boolean => {
  if (execution.id == null) return false
  return expandedRows.value.includes(execution.id)
}

const toggleExpanded = (execution: JobExecutionView) => {
  if (execution.id == null) return
  if (isExpanded(execution)) {
    expandedRows.value = expandedRows.value.filter((value) => value !== execution.id)
  } else {
    expandedRows.value = [...expandedRows.value, execution.id]
  }
}

const formatDate = (value?: string): string => {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

const formatDateNoSeconds = (value?: string): string => {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}

const relatedEntityTypeLabel = (type?: string): string => {
  return titleCase(type ?? "entity")
}

const statusTitle = (status?: string): string => {
  if (!status) return "Unknown"
  return titleCase(status)
}

const statusColor = (status?: string): string => {
  if (status === "SUCCESS") return "success"
  if (status === "FAILED") return "error"
  if (status === "RUNNING") return "info"
  if (status === "QUEUED") return "warning"
  return "secondary"
}

const rowStatusClass = (status?: string): string => {
  if (status === "SUCCESS") return "job-row--success"
  if (status === "FAILED") return "job-row--failed"
  if (status === "RUNNING") return "job-row--running"
  if (status === "QUEUED") return "job-row--queued"
  return ""
}

const showTriggerDialog = ref<boolean>(false)

const resetToFirstPageAndRefresh = () => {
  expandedRows.value = []
  if (page.value !== 1) {
    page.value = 1
    return
  }
  void refresh()
}

const onJobTriggered = () => {
  resetToFirstPageAndRefresh()
}

watch([selectedCategory, selectedStatus], () => {
  resetToFirstPageAndRefresh()
})

watch(searchQuery, () => {
  clearSearchDebounce()
  searchDebounceHandle = setTimeout(() => {
    searchDebounceHandle = undefined
    resetToFirstPageAndRefresh()
  }, 250)
})

onBeforeUnmount(() => {
  clearSearchDebounce()
})

watch(page, () => {
  expandedRows.value = []
  void refresh()
})

const loadStats = async () => {
  try {
    const response = await getStats()
    stats.value = response.data ?? null
  } catch {
    // Stats panel is supplementary; silently ignore errors
  }
}

const refresh = async () => {
  void loadStats()
  loading.value = true
  try {
    const normalizedSearch = (searchQuery.value ?? "").trim()
    const query: JobListQuery = {
      page: Math.max(0, page.value - 1),
      size: PAGE_SIZE,
      sort: ["updatedAt,desc", "id,desc"],
      ...(selectedCategory.value !== "all" ? {category: selectedCategory.value} : {}),
      ...(selectedStatus.value !== "all" ? {status: selectedStatus.value as JobExecution["status"]} : {}),
      ...(normalizedSearch ? {search: normalizedSearch} : {}),
    }

    const response = await list({query})
    if (response.status === 200) {
      const data = response.data
      if (Array.isArray(data)) {
        totalElements.value = data.length
        totalPages.value = Math.max(1, Math.ceil(data.length / PAGE_SIZE))
        const start = (page.value - 1) * PAGE_SIZE
        executions.value = data.slice(start, start + PAGE_SIZE) as JobExecutionView[]
      } else {
        const payload = (data ?? {}) as JobPage
        const content = (payload.content ?? []) as JobExecutionView[]
        const nextTotalElements = payload.page?.totalElements ?? content.length
        const nextTotalPages = Math.max(1, payload.page?.totalPages ?? Math.ceil(nextTotalElements / PAGE_SIZE))

        totalElements.value = nextTotalElements
        totalPages.value = nextTotalPages

        if (page.value > nextTotalPages) {
          page.value = nextTotalPages
          return
        }
        executions.value = content
      }
    } else {
      console.log(response.error)
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

const retry = async (execution: JobExecution) => {
  if (!execution?.id) return
  try {
    const response = await retryJob({path: {id: execution.id}})
    if (response.status === 200) {
      await refresh()
    } else {
      console.log(response.error)
    }
  } catch (error) {
    $handleNetworkError(error)
  }
}

const looksLikeStackTrace = (value?: string | null): boolean => {
  if (!value) return false
  return value.includes("\n\tat ") || value.includes("\n at ") || value.includes("Caused by:")
}

const titleCase = (value: string): string => {
  return value
    .replace(/[_.-]/g, " ")
    .split(/\s+/)
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1).toLowerCase())
    .join(" ")
}

onMounted(async () => {
  if (!store.getters.isAdmin) {
    await router.replace("/")
    return
  }
  void loadStats()
  await refresh()
})
</script>

<template>
  <v-main>
    <top-banner title="Job Manager" />

    <job-trigger-dialog
      v-model="showTriggerDialog"
      @enqueued="onJobTriggered"
    />

    <v-container>
      <div
        class="mx-auto my-3 job-manager-page"
      >
        <v-card
          v-if="stats !== null"
          class="manager-card mb-4"
          rounded="lg"
          variant="flat"
        >
          <v-row
            align="stretch"
            class="ma-0 pa-2"
            no-gutters
          >
            <v-col class="text-center px-2 py-1">
              <p class="text-overline mb-1">
                All-time
              </p>
              <v-row
                align="center"
                no-gutters
              >
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-total"
                >
                  <div class="text-h6 font-weight-bold">
                    {{ stats.totalCount }}
                  </div>
                  <div class="stats-label">
                    Total
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-success"
                >
                  <div
                    class="text-h6 font-weight-bold"
                    :class="stats.successCount > 0 ? 'text-success' : ''"
                  >
                    {{ stats.successCount }}
                    <span
                      v-if="stats.totalCount > 0"
                      class="text-body-2 font-weight-medium ml-1"
                    >{{ successRate }}%</span>
                  </div>
                  <div class="stats-label">
                    Succeeded
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-failed"
                >
                  <div
                    class="text-h6 font-weight-bold"
                    :class="stats.failedCount > 0 ? 'text-error' : ''"
                  >
                    {{ stats.failedCount }}
                  </div>
                  <div class="stats-label">
                    Failed
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-dead"
                >
                  <div
                    class="text-h6 font-weight-bold"
                    :class="stats.deadCount > 0 ? 'text-error' : ''"
                  >
                    {{ stats.deadCount }}
                  </div>
                  <div class="stats-label">
                    Dead
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-queued"
                >
                  <div
                    class="text-h6 font-weight-bold"
                    :class="stats.queuedCount > 0 ? 'text-warning' : ''"
                  >
                    {{ stats.queuedCount }}
                  </div>
                  <div class="stats-label">
                    Queued
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-running"
                >
                  <div
                    class="text-h6 font-weight-bold"
                    :class="stats.runningCount > 0 ? 'text-info' : ''"
                  >
                    {{ stats.runningCount }}
                  </div>
                  <div class="stats-label">
                    Running
                  </div>
                </v-col>
              </v-row>
            </v-col>

            <v-divider
              vertical
              class="mx-2"
            />

            <v-col
              class="text-center px-2 py-1"
              data-testid="job-stats-runtime"
            >
              <p class="text-overline mb-1">
                Since last startup
              </p>
              <v-row
                align="center"
                no-gutters
              >
                <v-col class="stats-cell">
                  <div
                    class="text-h6 font-weight-bold"
                    :class="stats.deadSinceStartup > 0 ? 'text-error' : ''"
                  >
                    {{ stats.deadSinceStartup.toFixed(0) }}
                  </div>
                  <div class="stats-label">
                    Dead
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col class="stats-cell">
                  <div
                    class="text-h6 font-weight-bold"
                    :class="stats.failedSinceStartup > 0 ? 'text-error' : ''"
                  >
                    {{ stats.failedSinceStartup.toFixed(0) }}
                  </div>
                  <div class="stats-label">
                    Failed
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col class="stats-cell">
                  <div class="text-h6 font-weight-bold">
                    {{ stats.avgSuccessDurationSeconds.toFixed(2) }}s
                  </div>
                  <div class="stats-label">
                    Avg. exec
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col class="stats-cell">
                  <div
                    class="text-h6 font-weight-bold"
                    :class="stats.recoveriesSinceStartup > 0 ? 'text-warning' : ''"
                  >
                    {{ stats.recoveriesSinceStartup.toFixed(0) }}
                  </div>
                  <div class="stats-label">
                    Recoveries
                  </div>
                </v-col>
              </v-row>
            </v-col>
          </v-row>
        </v-card>

        <v-card
          class="manager-card mb-4"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-1">
                Execution Monitor
              </p>
              <h2 class="text-h6 mb-1">
                Job overview
              </h2>
              <p class="text-caption text-medium-emphasis mb-0">
                Showing {{ pageRangeLabel }}
              </p>
            </div>

            <div class="d-flex ga-2">
              <v-btn
                color="primary"
                data-testid="job-manager-trigger-btn"
                variant="flat"
                @click="showTriggerDialog = true"
              >
                Trigger job
              </v-btn>
              <v-btn
                :disabled="loading"
                data-testid="job-manager-refresh-btn"
                variant="outlined"
                @click="refresh"
              >
                Refresh
              </v-btn>
            </div>
          </div>

          <div class="manager-card__body">
            <v-row class="ma-0 manager-filters">
              <v-col
                cols="12"
                md="3"
                sm="6"
              >
                <v-select
                  v-model="selectedCategory"
                  :items="categoryOptions"
                  data-testid="job-filter-category"
                  density="comfortable"
                  hide-details
                  item-title="title"
                  item-value="value"
                  label="Category"
                  variant="outlined"
                >
                  <template #item="{ props, internalItem }">
                    <v-list-item
                      v-bind="props"
                      :data-testid="`job-filter-category-option-${internalItem.value}`"
                    />
                  </template>
                </v-select>
              </v-col>
              <v-col
                cols="12"
                md="3"
                sm="6"
              >
                <v-select
                  v-model="selectedStatus"
                  :items="statusOptions"
                  data-testid="job-filter-status"
                  density="comfortable"
                  hide-details
                  item-title="title"
                  item-value="value"
                  label="Status"
                  variant="outlined"
                >
                  <template #item="{ props, internalItem }">
                    <v-list-item
                      v-bind="props"
                      :data-testid="`job-filter-status-option-${String(internalItem.value).toLowerCase()}`"
                    />
                  </template>
                </v-select>
              </v-col>
              <v-col
                cols="12"
                md="6"
                sm="12"
              >
                <v-text-field
                  v-model="searchQuery"
                  clearable
                  data-testid="job-filter-search"
                  density="comfortable"
                  hide-details
                  label="Search summary, type, actor or related entities"
                  prepend-inner-icon="mdi-magnify"
                  variant="outlined"
                />
              </v-col>
            </v-row>

            <div class="manager-chip-row">
              <v-chip
                size="small"
                variant="tonal"
              >
                Total {{ totalElements }}
              </v-chip>
              <v-chip
                color="warning"
                size="small"
                variant="tonal"
              >
                Queued {{ statusCounts.QUEUED }}
              </v-chip>
              <v-chip
                color="info"
                size="small"
                variant="tonal"
              >
                Running {{ statusCounts.RUNNING }}
              </v-chip>
              <v-chip
                color="success"
                size="small"
                variant="tonal"
              >
                Success {{ statusCounts.SUCCESS }}
              </v-chip>
              <v-chip
                color="error"
                size="small"
                variant="tonal"
              >
                Failed {{ statusCounts.FAILED }}
              </v-chip>
              <v-chip
                color="error"
                size="small"
                variant="tonal"
              >
                Dead {{ statusCounts.DEAD }}
              </v-chip>
              <span
                v-if="loading"
                class="text-caption text-medium-emphasis"
              >
                Refreshing...
              </span>
            </div>
          </div>
        </v-card>

        <v-card
          class="manager-card"
          rounded="lg"
          variant="flat"
        >
          <v-list
            data-testid="job-manager-table"
            density="comfortable"
          >
            <v-list-item
              v-if="executions.length === 0"
              subtitle="Try adjusting your filters or refresh the page."
              title="No job executions found."
            />

            <template
              v-for="execution in executions"
              :key="execution.id"
            >
              <v-list-item
                :data-testid="`job-row-${execution.id}`"
                :aria-expanded="isExpanded(execution)"
                :aria-label="`Toggle details for job ${execution.id}`"
                :class="['job-row', rowStatusClass(execution.status), {'job-row--expanded': isExpanded(execution)}]"
                role="button"
                tabindex="0"
                @click="toggleExpanded(execution)"
                @keydown.enter.prevent="toggleExpanded(execution)"
                @keydown.space.prevent="toggleExpanded(execution)"
              >
                <template
                  v-if="execution.category && execution.category !== 'other'"
                  #prepend
                >
                  <v-chip
                    class="mr-2 job-category-pill"
                    size="small"
                    variant="tonal"
                  >
                    {{ titleCase(execution.category) }}
                  </v-chip>
                </template>

                <v-list-item-title class="job-row-title-slot">
                  <div class="job-preview">
                    <p
                      class="job-title"
                      :title="previewTitle(execution)"
                    >
                      {{ previewTitle(execution) }}
                    </p>

                    <div
                      v-if="payloadChips(execution).length"
                      class="job-payload-chips"
                      :data-testid="`job-row-payload-${execution.id}`"
                    >
                      <v-chip
                        v-for="chip in payloadChips(execution)"
                        :key="chip.key"
                        size="x-small"
                        variant="tonal"
                      >
                        <strong>{{ chip.label }}:</strong>&nbsp;{{ chip.value }}
                      </v-chip>
                    </div>

                    <div class="job-meta-inline">
                      <span>{{ previewActorDisplay(execution) }}</span>
                      <span class="job-meta-sep">·</span>
                      <span>{{ attemptsLabel(execution.attempts) }}</span>
                      <span class="job-meta-sep">·</span>
                      <span>{{ formatDateNoSeconds(execution.queuedAt) }}</span>
                    </div>
                  </div>
                </v-list-item-title>

                <template #append>
                  <div class="job-row-actions">
                    <v-chip
                      :color="statusColor(execution.status)"
                      size="small"
                      variant="tonal"
                    >
                      {{ statusTitle(execution.status) }}
                    </v-chip>

                    <v-btn
                      v-if="execution.status === 'FAILED' || execution.status === 'DEAD'"
                      :data-testid="`job-retry-btn-${execution.id}`"
                      size="small"
                      variant="outlined"
                      @click.stop="retry(execution)"
                    >
                      Retry
                    </v-btn>
                  </div>
                </template>
              </v-list-item>

              <v-expand-transition>
                <div
                  v-if="isExpanded(execution)"
                  :data-testid="`job-detail-${execution.id}`"
                  class="job-detail px-4 pb-3"
                >
                  <p
                    v-if="jobDescription(execution)"
                    class="job-description-expanded text-caption text-medium-emphasis mb-3"
                  >
                    {{ jobDescription(execution) }}
                  </p>

                  <div class="job-detail-grid">
                    <v-sheet
                      class="detail-panel"
                      rounded="md"
                      variant="tonal"
                    >
                      <p class="text-caption text-medium-emphasis mb-2">
                        Execution
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>ID:</strong> {{ execution.id }}
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Category:</strong> {{ titleCase(execution.category ?? "other") }}
                      </p>
                      <p class="text-body-2 mb-0">
                        <strong>Status:</strong> {{ statusTitle(execution.status) }}
                      </p>
                    </v-sheet>

                    <v-sheet
                      class="detail-panel"
                      rounded="md"
                      variant="tonal"
                    >
                      <p class="text-caption text-medium-emphasis mb-2">
                        Trigger
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Actor:</strong> {{ actorDisplay(execution) }}
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Attempts:</strong> {{ attemptsLabel(execution.attempts) }}
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Queued:</strong> {{ formatDate(execution.queuedAt) }}
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Started:</strong> {{ formatDate(execution.startedAt) }}
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Finished:</strong> {{ formatDate(execution.finishedAt) }}
                      </p>
                      <p
                        v-if="execution.nextAttemptAt"
                        class="text-body-2 mb-0"
                      >
                        <strong>Next attempt:</strong> {{ formatDate(execution.nextAttemptAt) }}
                      </p>
                    </v-sheet>
                  </div>

                  <v-sheet
                    class="detail-panel mt-3"
                    rounded="md"
                    variant="tonal"
                  >
                    <p class="text-caption text-medium-emphasis mb-2">
                      Related entities
                    </p>
                    <div
                      v-if="(execution.relatedEntities ?? []).length"
                      class="related-entity-list"
                    >
                      <div
                        v-for="entity in execution.relatedEntities"
                        :key="`${entity.type}-${entity.id}`"
                        class="related-entity-row"
                      >
                        <span class="related-entity-type">
                          {{ relatedEntityTypeLabel(entity.type) }}
                        </span>
                        <v-divider
                          class="job-divider"
                          vertical
                        />
                        <span class="related-entity-label">
                          {{ entity.label ?? `${relatedEntityTypeLabel(entity.type)} #${entity.id}` }}
                        </span>
                      </div>
                    </div>
                    <p
                      v-else
                      class="text-body-2 text-medium-emphasis mb-0"
                    >
                      No related entities.
                    </p>
                  </v-sheet>

                  <v-sheet
                    v-if="execution.errorType || errorSummary(execution) !== '-'"
                    class="detail-panel mt-3"
                    rounded="md"
                    variant="tonal"
                  >
                    <p class="text-caption text-medium-emphasis mb-2">
                      Failure detail
                    </p>
                    <p
                      :data-testid="`job-error-reason-${execution.id}`"
                      class="text-body-2 mb-2"
                    >
                      <strong>Message:</strong> {{ errorSummary(execution) }}
                    </p>
                    <pre
                      v-if="hasStackTrace(execution)"
                      :data-testid="`job-stacktrace-${execution.id}`"
                      class="stacktrace"
                    >{{ stackTrace(execution) }}</pre>
                  </v-sheet>
                </div>
              </v-expand-transition>

              <v-divider class="job-divider mx-4" />
            </template>
          </v-list>

          <div class="manager-pagination">
            <span class="text-caption text-medium-emphasis">
              Page {{ page }} of {{ totalPages }}
            </span>
            <v-pagination
              v-model="page"
              :length="totalPages"
              :total-visible="7"
              data-testid="job-manager-pagination"
            />
          </div>
        </v-card>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.job-manager-page {
  max-width: 980px;
}

.manager-card {
  background: rgba(var(--v-theme-surface), 0.92);
  box-shadow: none;
}

.manager-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px 8px;
}

.manager-card__body {
  padding: 8px 14px 12px;
}

.stats-label {
  font-size: 10px;
  line-height: 1.1;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.6);
}

.stats-cell {
  padding: 2px 6px;
}

.manager-filters {
  row-gap: 6px;
}

.manager-chip-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.job-row {
  align-items: flex-start;
  border-left: 3px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s ease, outline-color 0.15s ease;
  outline: 1px solid transparent;
}

.job-row:hover,
.job-row:focus-visible {
  background: rgba(var(--v-theme-on-surface), 0.035);
  outline-color: rgba(var(--v-theme-primary), 0.35);
}

.job-row--expanded {
  background: rgba(var(--v-theme-primary), 0.06);
  outline-color: rgba(var(--v-theme-primary), 0.42);
}

.job-row--success {
  border-left-color: rgba(var(--v-theme-success), 0.7);
}

.job-row--failed {
  border-left-color: rgba(var(--v-theme-error), 0.72);
}

.job-row--running {
  border-left-color: rgba(var(--v-theme-info), 0.7);
}

.job-row--queued {
  border-left-color: rgba(var(--v-theme-warning), 0.7);
}

.job-category-pill {
  min-height: 28px;
  height: 28px;
  padding-inline: 10px;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.job-preview {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.job-title {
  margin: 0;
  font-size: 15px;
  line-height: 1.2;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.job-description-expanded {
  white-space: normal;
}

.job-payload-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
}

.job-meta-inline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 0;
  font-size: 12.5px;
  line-height: 1.2;
  color: rgba(var(--v-theme-on-surface), 0.7);
}

.job-meta-sep {
  opacity: 0.5;
}

.job-divider {
  border-color: rgba(var(--v-theme-success), 0.45);
  opacity: 1;
}

.job-row-title-slot {
  /*
   * Vuetify's default v-list-item-title sets margin-bottom to keep
   * space between title and subtitle. We render title + meta in a
   * single flex stack, so collapse that gap to keep the row visually
   * tight.
   */
  margin-bottom: 0 !important;
  white-space: normal;
}

.job-row-title-slot :deep(.v-list-item-subtitle) {
  display: none;
}

.job-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 96px;
}

.job-detail {
  margin-top: 4px;
}

.job-description-expanded {
  font-style: italic;
}

.job-detail-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.detail-panel {
  padding: 12px;
  background: rgba(var(--v-theme-on-surface), 0.025);
}

.related-entity-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.related-entity-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
}

.related-entity-type {
  min-width: 128px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: rgba(var(--v-theme-on-surface), 0.65);
}

.related-entity-label {
  font-size: 14px;
}

.manager-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 18px 18px;
}

.stacktrace {
  max-height: 280px;
  overflow: auto;
  padding: 10px;
  border-radius: 6px;
  background: rgba(var(--v-theme-on-surface), 0.06);
  color: rgb(var(--v-theme-on-surface));
  font-size: 12px;
  line-height: 1.35;
  white-space: pre-wrap;
  word-break: break-word;
}

.stats-cell {
  padding: 4px 8px;
}

@media (max-width: 900px) {
  .job-preview {
    grid-template-columns: 1fr;
  }

  .job-meta-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .job-detail-grid {
    grid-template-columns: 1fr;
  }

  .job-row-actions {
    min-width: 0;
  }

  .manager-pagination {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 640px) {
  .job-meta-grid {
    grid-template-columns: 1fr;
  }

  .job-meta-cell {
    padding: 0;
  }

  .job-meta-cell + .job-meta-cell {
    border-left: 0;
    border-top: 1px solid rgba(var(--v-theme-success), 0.45);
    margin-top: 4px;
    padding-top: 4px;
  }
}
</style>
