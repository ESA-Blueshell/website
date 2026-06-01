<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import JobTriggerDialog from "@/components/common/modals/JobTriggerDialog.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {JobExecutionCategory, JobExecutionStatus, type JobExecution, type JobStatsDto, getStats, list, retry as retryJob} from "@/services/api"
import store from "@/plugins/store"
import {attemptsLabel} from "@/utils/jobAttempts"

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

const CONTACT_SYSTEM_LABELS: Record<NonNullable<JobExecutionView["targetSystem"]>, string> = {
  BREVO: "Brevo",
}

const humanizeJobType = (jobType: string): string =>
  jobType
    .replace(/[._-]+/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1).toLowerCase())
    .join(" ")

const summarizeExecution = (execution: JobExecutionView): string => {
  const jobType = execution.jobType ?? ""
  const primary = execution.relatedEntities?.[0]?.label
  const system = execution.targetSystem ? CONTACT_SYSTEM_LABELS[execution.targetSystem] : undefined

  switch (jobType) {
    case "contact.sync-all":
      return "Sync all contacts to every system"
    case "contact.list-sync-all":
      return "Sync all list memberships to every system"
    case "contact.period-list-sync-all":
      return "Reconcile contribution-period lists"
    case "contact.sync-to-system":
      if (system && primary) return `Sync contact to ${system} for ${primary}`
      if (system) return `Sync contact to ${system}`
      return primary ? `Sync contact for ${primary}` : "Sync contact"
    case "contact.list-sync":
      if (system && primary) return `Sync list membership to ${system} for ${primary}`
      if (system) return `Sync list membership to ${system}`
      return primary ? `Sync list membership for ${primary}` : "Sync list membership"
    case "contact.delete":
      return primary ? `Delete contact for ${primary}` : "Delete contact"
    case "contact.process-list-membership":
      return primary ? `Process list membership for ${primary}` : "Process list membership"
    case "email.recovery":
      return primary ? `Recovery email for ${primary}` : "Recovery email"
    case "email.event-signup":
      return primary ? `Event sign-up email for ${primary}` : "Event sign-up email"
    case "email.contribution-reminder":
      return primary ? `Contribution reminder for ${primary}` : "Contribution reminder email"
  }
  if (jobType.startsWith("calendar.")) {
    return primary ? `Calendar sync for ${primary}` : "Calendar synchronization"
  }
  if (jobType.startsWith("contact.")) {
    return primary ? `Contact sync for ${primary}` : "Contact synchronization"
  }
  return humanizeJobType(jobType)
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

const statusCounts = computed(() => {
  const counts = Object.fromEntries(
    Object.values(JobExecutionStatus).map((status) => [status, 0]),
  ) as Record<JobExecutionStatus, number>

  for (const execution of executions.value) {
    const status = execution.status
    if (status && status in counts) {
      counts[status] += 1
    }
  }

  return counts
})

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

    <div class="mx-3">
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
            class="ma-0 pa-3"
            no-gutters
          >
            <v-col class="text-center px-3 py-1">
              <p class="text-h6 font-weight-medium mb-2">
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
                  <div class="text-h5 font-weight-bold">
                    {{ stats.totalCount }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Total
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-success"
                >
                  <div
                    class="text-h5 font-weight-bold"
                    :class="stats.successCount > 0 ? 'text-success' : ''"
                  >
                    {{ stats.successCount }}
                    <span
                      v-if="stats.totalCount > 0"
                      class="text-body-2 font-weight-medium ml-1"
                    >{{ successRate }}%</span>
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Succeeded
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-failed"
                >
                  <div
                    class="text-h5 font-weight-bold"
                    :class="stats.failedCount > 0 ? 'text-error' : ''"
                  >
                    {{ stats.failedCount }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Failed
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="job-stats-dead"
                >
                  <div
                    class="text-h5 font-weight-bold"
                    :class="stats.deadCount > 0 ? 'text-error' : ''"
                  >
                    {{ stats.deadCount }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Dead
                  </div>
                </v-col>
              </v-row>
            </v-col>

            <v-divider
              vertical
              class="mx-2"
            />

            <v-col
              class="text-center px-3 py-1"
              data-testid="job-stats-runtime"
            >
              <p class="text-h6 font-weight-medium mb-2">
                Since last startup
              </p>
              <v-row
                align="center"
                no-gutters
              >
                <v-col class="stats-cell">
                  <div
                    class="text-h5 font-weight-bold"
                    :class="stats.deadSinceStartup > 0 ? 'text-error' : ''"
                  >
                    {{ stats.deadSinceStartup.toFixed(0) }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Dead
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col class="stats-cell">
                  <div
                    class="text-h5 font-weight-bold"
                    :class="stats.failedSinceStartup > 0 ? 'text-error' : ''"
                  >
                    {{ stats.failedSinceStartup.toFixed(0) }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Failed
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col class="stats-cell">
                  <div class="text-h5 font-weight-bold">
                    {{ stats.avgSuccessDurationSeconds.toFixed(2) }}s
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Avg. exec
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col class="stats-cell">
                  <div
                    class="text-h5 font-weight-bold"
                    :class="stats.recoveriesSinceStartup > 0 ? 'text-warning' : ''"
                  >
                    {{ stats.recoveriesSinceStartup.toFixed(0) }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
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
                :class="['job-row py-2', rowStatusClass(execution.status), {'job-row--expanded': isExpanded(execution)}]"
                role="button"
                tabindex="0"
                @click="toggleExpanded(execution)"
                @keydown.enter.prevent="toggleExpanded(execution)"
                @keydown.space.prevent="toggleExpanded(execution)"
              >
                <template #prepend>
                  <v-chip
                    class="mr-3 job-category-pill"
                    size="small"
                    variant="tonal"
                  >
                    {{ titleCase(execution.category ?? "other") }}
                  </v-chip>
                </template>

                <v-list-item-title class="mb-0">
                  <div class="job-preview">
                    <p class="job-title">
                      {{ previewTitle(execution) }}
                    </p>

                    <div class="job-meta-grid">
                      <div class="job-meta-cell">
                        <span class="job-meta-label">Triggered by</span>
                        <span class="job-meta-value">{{ previewActorDisplay(execution) }}</span>
                      </div>
                      <div class="job-meta-cell">
                        <span class="job-meta-label">Attempts</span>
                        <span class="job-meta-value">{{ attemptsLabel(execution.attempts) }}</span>
                      </div>
                      <div class="job-meta-cell">
                        <span class="job-meta-label">Queued at</span>
                        <span class="job-meta-value">{{ formatDateNoSeconds(execution.queuedAt) }}</span>
                      </div>
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
                  class="job-detail px-4 pb-4"
                >
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
    </div>
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
  gap: 12px;
  padding: 18px 18px 14px;
}

.manager-card__body {
  padding: 14px 18px 18px;
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
  min-height: 34px;
  width: 108px;
  max-width: 108px;
  min-width: 108px;
  padding-inline: 8px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  justify-content: center;
}

.job-category-pill :deep(.v-chip__content) {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.job-preview {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1.6fr);
  gap: 10px 12px;
  align-items: center;
}

.job-title {
  margin: 0;
  font-size: 15px;
  line-height: 1.3;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: normal;
}

.job-meta-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr) minmax(0, 1.55fr);
  gap: 0;
}

.job-meta-cell {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 0 10px;
}

.job-meta-cell + .job-meta-cell {
  border-left: 1px solid rgba(var(--v-theme-success), 0.45);
}

.job-meta-label {
  font-size: 10px;
  line-height: 1.2;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: rgba(var(--v-theme-on-surface), 0.58);
}

.job-meta-value {
  font-size: 13px;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.job-divider {
  border-color: rgba(var(--v-theme-success), 0.45);
  opacity: 1;
}

.job-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 140px;
}

.job-detail {
  margin-top: 6px;
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
