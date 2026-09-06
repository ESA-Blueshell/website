<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import JobTriggerDialog from "@/components/common/modals/JobTriggerDialog.vue"
import {loadJobPage, loadJobStats, retryJob} from "@/domains/jobs/adapters/jobs"
import {
  type Job,
  type JobStats,
  JobExecutionCategory,
  JobExecutionStatus,
  actorDisplay,
  canRetry,
  categoryOptions as jobCategoryOptions,
  errorSummary,
  hasStackTrace,
  jobDescription,
  payloadChips,
  previewActorDisplay,
  previewTitle,
  relatedEntityLabel,
  relatedEntityTypeLabel,
  rowStatusClass,
  stackTrace,
  statusColor,
  statusCounts as countsOf,
  statusOptions as jobStatusOptions,
  statusTitle,
  successRate as rateOf,
  titleCase,
} from "@/domains/jobs"
import {usePagedTable, type PageQuery} from "@/composables/usePagedTable"
import store from "@/plugins/store"
import {attemptsLabel} from "@/utils/jobAttempts"
import {formatDate, formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "JobManagerPage"})

const router = useRouter()
const PAGE_SIZE = 50

const stats = ref<JobStats | null>(null)
const selectedCategory = ref<JobExecutionCategory | "all">("all")
const selectedStatus = ref<JobExecutionStatus | "all">("all")
const showTriggerDialog = ref(false)

const loadStats = async () => {
  stats.value = await loadJobStats()
}

// Stats ride along with the rows so the panel and the table describe the same moment, and are
// not awaited: the panel is supplementary and a slow count must not hold the table back.
const loadPage = (query: PageQuery) => {
  void loadStats()
  return loadJobPage(query, {
    ...(selectedCategory.value !== "all" ? {category: selectedCategory.value} : {}),
    ...(selectedStatus.value !== "all" ? {status: selectedStatus.value} : {}),
  })
}

const table = usePagedTable<Job>(loadPage, {pageSize: PAGE_SIZE})
const {
  rows: executions,
  loading,
  page,
  totalPages,
  totalElements,
  search: searchQuery,
  pageRangeLabel,
  isExpanded,
  toggleExpanded,
  refresh,
  resetToFirstPage,
} = table

watch([selectedCategory, selectedStatus], () => {
  resetToFirstPage()
})

/** A job just enqueued belongs at the top, which is the first page with the filters unchanged. */
const onJobTriggered = () => {
  resetToFirstPage()
}

// Read once: both lists come from the generated enums, which do not change while the page is open.
const categoryOptions = jobCategoryOptions()
const statusOptions = jobStatusOptions()

const successRate = computed(() => rateOf(stats.value))
const statusCounts = computed(() => countsOf(stats.value))

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
                      v-if="payloadChips(execution.payload).length"
                      class="job-payload-chips"
                      :data-testid="`job-row-payload-${execution.id}`"
                    >
                      <v-chip
                        v-for="chip in payloadChips(execution.payload)"
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
                      v-if="canRetry(execution)"
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
                          {{ relatedEntityLabel(entity) }}
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
