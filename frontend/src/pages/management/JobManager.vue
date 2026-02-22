<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import store from "@/plugins/store"
import {type JobExecution, list, retry as retryJob} from "@/services/api"

defineOptions({name: "JobManagerPage"})

const PAGE_SIZE = 50

const router = useRouter()

type JobRelatedEntity = {
  type?: string
  id?: number
  label?: string
}

type JobExecutionView = JobExecution & {
  category?: string
  summary?: string
  stackTrace?: string | null
  initiatedByDisplay?: string
  initiatedByUsername?: string | null
  initiatedByFullName?: string | null
  relatedEntities?: JobRelatedEntity[]
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
  category?: string
  status?: JobExecution["status"]
  search?: string
}

const executions = ref<JobExecutionView[]>([])
const loading = ref<boolean>(false)
const selectedCategory = ref<string>("all")
const selectedStatus = ref<string>("all")
const searchQuery = ref<string>("")
const expandedRows = ref<number[]>([])
const page = ref<number>(1)
const totalPages = ref<number>(1)
const totalElements = ref<number>(0)
let searchDebounceHandle: ReturnType<typeof setTimeout> | undefined

const statusCounts = computed(() => {
  const counts: Record<string, number> = {
    QUEUED: 0,
    RUNNING: 0,
    SUCCESS: 0,
    FAILED: 0,
  }

  for (const execution of executions.value) {
    const status = execution.status
    if (status && Object.prototype.hasOwnProperty.call(counts, status)) {
      counts[status] = (counts[status] ?? 0) + 1
    }
  }

  return counts
})

const categoryOptions = computed(() => {
  const values = Array.from(
    new Set(
      executions.value
        .map((execution) => execution.category)
        .filter((category): category is string => !!category),
    ),
  ).sort((a, b) => a.localeCompare(b))
  if (selectedCategory.value !== "all" && !values.includes(selectedCategory.value)) {
    values.push(selectedCategory.value)
  }

  return [
    {title: "All categories", value: "all"},
    ...values.map((value) => ({title: titleCase(value), value})),
  ]
})

const statusOptions = [
  {title: "All statuses", value: "all"},
  {title: "Queued", value: "QUEUED"},
  {title: "Running", value: "RUNNING"},
  {title: "Success", value: "SUCCESS"},
  {title: "Failed", value: "FAILED"},
]

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

const previewTitle = (execution: JobExecutionView): string => {
  if (execution.summary?.trim()) return execution.summary
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

const attemptsLabel = (attempts?: number): string => {
  const count = attempts ?? 0
  return `${count} ${count === 1 ? "attempt" : "attempts"}`
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

const resetToFirstPageAndRefresh = () => {
  expandedRows.value = []
  if (page.value !== 1) {
    page.value = 1
    return
  }
  void refresh()
}

watch([selectedCategory, selectedStatus], () => {
  resetToFirstPageAndRefresh()
})

watch(searchQuery, () => {
  if (searchDebounceHandle) {
    clearTimeout(searchDebounceHandle)
  }
  searchDebounceHandle = setTimeout(() => {
    resetToFirstPageAndRefresh()
  }, 250)
})

watch(page, () => {
  expandedRows.value = []
  void refresh()
})

const refresh = async () => {
  loading.value = true
  try {
    const query: JobListQuery = {
      page: Math.max(0, page.value - 1),
      size: PAGE_SIZE,
      sort: ["createdAt,desc", "id,desc"],
      ...(selectedCategory.value !== "all" ? {category: selectedCategory.value} : {}),
      ...(selectedStatus.value !== "all" ? {status: selectedStatus.value as JobExecution["status"]} : {}),
      ...(searchQuery.value.trim() ? {search: searchQuery.value.trim()} : {}),
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
    .replace(/[_.\-]/g, " ")
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
  await refresh()
})
</script>

<template>
  <v-main>
    <top-banner title="Job Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-3 job-manager-page"
      >
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

            <v-btn
              :disabled="loading"
              color="primary"
              data-testid="job-manager-refresh-btn"
              variant="flat"
              @click="refresh"
            >
              Refresh
            </v-btn>
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
                  density="comfortable"
                  hide-details
                  item-title="title"
                  item-value="value"
                  label="Category"
                  variant="outlined"
                />
              </v-col>
              <v-col
                cols="12"
                md="3"
                sm="6"
              >
                <v-select
                  v-model="selectedStatus"
                  :items="statusOptions"
                  density="comfortable"
                  hide-details
                  item-title="title"
                  item-value="value"
                  label="Status"
                  variant="outlined"
                />
              </v-col>
              <v-col
                cols="12"
                md="6"
                sm="12"
              >
                <v-text-field
                  v-model="searchQuery"
                  clearable
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
                :class="['job-row py-3', rowStatusClass(execution.status), {'job-row--expanded': isExpanded(execution)}]"
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

                <v-list-item-title class="text-body-1 font-weight-medium mb-1">
                  {{ previewTitle(execution) }}
                </v-list-item-title>

                <v-list-item-subtitle class="text-caption">
                  <div class="job-meta-preview">
                    <span>Triggered by <strong>{{ actorDisplay(execution) }}</strong></span>
                    <v-divider
                      class="job-divider"
                      vertical
                    />
                    <span>{{ attemptsLabel(execution.attempts) }}</span>
                    <v-divider
                      class="job-divider"
                      vertical
                    />
                    <span>Queued {{ formatDate(execution.queuedAt) }}</span>
                  </div>
                </v-list-item-subtitle>

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
                      v-if="execution.status === 'FAILED'"
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
                      <p class="text-body-2 mb-0">
                        <strong>Finished:</strong> {{ formatDate(execution.finishedAt) }}
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
  padding-inline: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.job-meta-preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  line-height: 1.5;
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
  min-width: 160px;
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

@media (max-width: 900px) {
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
</style>
