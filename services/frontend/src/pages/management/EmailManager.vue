<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {getStats1, list1, retry1, type Email, type EmailStats} from "@/services/api"

defineOptions({name: "EmailManagerPage"})

type EmailDeliveryStatus = NonNullable<Email["deliveryStatus"]>

const PAGE_SIZE = 50

const emails = ref<Email[]>([])
const loading = ref(false)
const retrying = ref<number | null>(null)
const stats = ref<EmailStats | null>(null)
const selectedStatus = ref<EmailDeliveryStatus | "all">("all")
const searchQuery = ref<string>("")
const expandedRows = ref<number[]>([])
const page = ref(1)
const totalPages = ref(1)
const totalElements = ref(0)
let searchDebounceHandle: ReturnType<typeof setTimeout> | undefined

const clearSearchDebounce = () => {
  if (searchDebounceHandle) {
    clearTimeout(searchDebounceHandle)
    searchDebounceHandle = undefined
  }
}

const deliveryRate = computed(() => {
  if (!stats.value || (stats.value.totalCount ?? 0) === 0) return 0
  const delivered = (stats.value.deliveredCount ?? 0) + (stats.value.openedCount ?? 0)
  return Math.round((delivered / (stats.value.totalCount ?? 1)) * 100)
})

const openRate = computed(() => {
  if (!stats.value || (stats.value.totalCount ?? 0) === 0) return 0
  return Math.round(((stats.value.openedCount ?? 0) / (stats.value.totalCount ?? 1)) * 100)
})

const statusOptions = [
  {title: "All statuses", value: "all"},
  {title: "Pending", value: "PENDING"},
  {title: "Sent", value: "SENT"},
  {title: "Delivered", value: "DELIVERED"},
  {title: "Opened", value: "OPENED"},
  {title: "Bounced", value: "BOUNCED"},
  {title: "Failed", value: "FAILED"},
]

const statusCounts = computed(() => {
  const counts: Record<string, number> = {
    PENDING: 0,
    SENT: 0,
    DELIVERED: 0,
    OPENED: 0,
    BOUNCED: 0,
    FAILED: 0,
  }
  for (const email of emails.value) {
    const s = email.deliveryStatus
    if (s && Object.prototype.hasOwnProperty.call(counts, s)) {
      counts[s] = (counts[s] ?? 0) + 1
    }
  }
  return counts
})

const pageRangeLabel = computed<string>(() => {
  if (totalElements.value === 0 || emails.value.length === 0) return `0 of ${totalElements.value}`
  const start = (page.value - 1) * PAGE_SIZE + 1
  const end = Math.min(start + emails.value.length - 1, totalElements.value)
  return `${start}-${end} of ${totalElements.value}`
})

const isExpanded = (email: Email): boolean => {
  if (email.id == null) return false
  return expandedRows.value.includes(email.id)
}

const toggleExpanded = (email: Email) => {
  if (email.id == null) return
  if (isExpanded(email)) {
    expandedRows.value = expandedRows.value.filter((id) => id !== email.id)
  } else {
    expandedRows.value = [...expandedRows.value, email.id!]
  }
}

const formatDate = (value?: string): string => {
  if (!value) return "-"
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
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

const statusColor = (status?: EmailDeliveryStatus): string => {
  if (status === "DELIVERED" || status === "OPENED") return "success"
  if (status === "FAILED" || status === "BOUNCED") return "error"
  if (status === "SENT") return "info"
  if (status === "PENDING") return "warning"
  return "secondary"
}

const rowStatusClass = (status?: EmailDeliveryStatus): string => {
  if (status === "DELIVERED" || status === "OPENED") return "email-row--success"
  if (status === "FAILED" || status === "BOUNCED") return "email-row--failed"
  if (status === "SENT") return "email-row--sent"
  if (status === "PENDING") return "email-row--pending"
  return ""
}

const canRetry = (email: Email): boolean => {
  return email.deliveryStatus === "FAILED" && email.jobExecutionId != null
}

const resetToFirstPageAndRefresh = () => {
  expandedRows.value = []
  if (page.value !== 1) {
    page.value = 1
    return
  }
  void refresh()
}

watch(selectedStatus, () => {
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
    const response = await getStats1()
    stats.value = response.data ?? null
  } catch {
    // Stats panel is supplementary; silently ignore errors
  }
}

const refresh = async () => {
  void loadStats()
  loading.value = true
  try {
    const response = await list1({
      query: {
        page: page.value - 1,
        size: PAGE_SIZE,
        sort: ["createdAt,desc"],
        deliveryStatus: selectedStatus.value !== "all" ? selectedStatus.value : undefined,
        search: searchQuery.value?.trim() || undefined,
      },
    })
    const data = response.data
    emails.value = data?.content ?? []
    totalElements.value = data?.page?.totalElements ?? 0
    totalPages.value = data?.page?.totalPages ?? 1
  } catch (e) {
    $handleNetworkError(e)
  } finally {
    loading.value = false
  }
}

const retryEmail = async (email: Email) => {
  if (email.id == null) return
  retrying.value = email.id
  try {
    await retry1({path: {id: email.id}})
    await Promise.all([refresh(), loadStats()])
  } catch (e) {
    $handleNetworkError(e)
  } finally {
    retrying.value = null
  }
}

onMounted(() => {
  void Promise.all([loadStats(), refresh()])
})
</script>

<template>
  <v-main>
    <top-banner title="Email Manager" />

    <v-container>
      <div class="mx-auto my-3 email-manager-page">
        <!-- Stats panel -->
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
                All-time delivery
              </p>
              <v-row
                align="center"
                no-gutters
              >
                <v-col
                  class="stats-cell"
                  data-testid="email-stats-total"
                >
                  <div class="text-h5 font-weight-bold">
                    {{ stats.totalCount ?? "—" }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Total
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="email-stats-sent"
                >
                  <div
                    class="text-h5 font-weight-bold"
                    :class="(stats.sentCount ?? 0) > 0 ? 'text-info' : ''"
                  >
                    {{ stats.sentCount ?? "—" }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Sent
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="email-stats-delivered"
                >
                  <div
                    class="text-h5 font-weight-bold"
                    :class="(stats.deliveredCount ?? 0) > 0 ? 'text-success' : ''"
                  >
                    {{ stats.deliveredCount ?? "—" }}
                    <span
                      v-if="(stats.totalCount ?? 0) > 0"
                      class="text-body-2 font-weight-medium ml-1"
                    >{{ deliveryRate }}%</span>
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Delivered
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="email-stats-opened"
                >
                  <div
                    class="text-h5 font-weight-bold"
                    :class="(stats.openedCount ?? 0) > 0 ? 'text-success' : ''"
                  >
                    {{ stats.openedCount ?? "—" }}
                    <span
                      v-if="(stats.totalCount ?? 0) > 0"
                      class="text-body-2 font-weight-medium ml-1"
                    >{{ openRate }}%</span>
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Opened
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="email-stats-bounced"
                >
                  <div
                    class="text-h5 font-weight-bold"
                    :class="(stats.bouncedCount ?? 0) > 0 ? 'text-error' : ''"
                  >
                    {{ stats.bouncedCount ?? "—" }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Bounced
                  </div>
                </v-col>
                <v-divider vertical />
                <v-col
                  class="stats-cell"
                  data-testid="email-stats-failed"
                >
                  <div
                    class="text-h5 font-weight-bold"
                    :class="(stats.failedCount ?? 0) > 0 ? 'text-error' : ''"
                  >
                    {{ stats.failedCount ?? "—" }}
                  </div>
                  <div class="text-caption text-medium-emphasis text-uppercase">
                    Failed
                  </div>
                </v-col>
              </v-row>
            </v-col>
          </v-row>
        </v-card>

        <!-- Filters + header -->
        <v-card
          class="manager-card mb-4"
          rounded="lg"
          variant="flat"
        >
          <div class="manager-card__header">
            <div>
              <p class="text-overline mb-1">
                Delivery Monitor
              </p>
              <h2 class="text-h6 mb-1">
                Email overview
              </h2>
              <p class="text-caption text-medium-emphasis mb-0">
                Showing {{ pageRangeLabel }}
              </p>
            </div>

            <v-btn
              :disabled="loading"
              color="primary"
              data-testid="email-manager-refresh-btn"
              variant="flat"
              @click="resetToFirstPageAndRefresh"
            >
              Refresh
            </v-btn>
          </div>

          <div class="manager-card__body">
            <v-row class="ma-0 manager-filters">
              <v-col
                cols="12"
                md="4"
                sm="6"
              >
                <v-select
                  v-model="selectedStatus"
                  :items="statusOptions"
                  data-testid="email-filter-status"
                  density="comfortable"
                  hide-details
                  item-title="title"
                  item-value="value"
                  label="Status"
                  variant="outlined"
                >
                  <template #item="{props, internalItem}">
                    <v-list-item
                      v-bind="props"
                      :data-testid="`email-filter-status-option-${String(internalItem.value).toLowerCase()}`"
                    />
                  </template>
                </v-select>
              </v-col>
              <v-col
                cols="12"
                md="8"
                sm="6"
              >
                <v-text-field
                  v-model="searchQuery"
                  clearable
                  data-testid="email-filter-search"
                  density="comfortable"
                  hide-details
                  label="Search recipient or subject"
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
                Pending {{ statusCounts.PENDING }}
              </v-chip>
              <v-chip
                color="info"
                size="small"
                variant="tonal"
              >
                Sent {{ statusCounts.SENT }}
              </v-chip>
              <v-chip
                color="success"
                size="small"
                variant="tonal"
              >
                Delivered {{ statusCounts.DELIVERED }}
              </v-chip>
              <v-chip
                color="success"
                size="small"
                variant="tonal"
              >
                Opened {{ statusCounts.OPENED }}
              </v-chip>
              <v-chip
                color="error"
                size="small"
                variant="tonal"
              >
                Bounced {{ statusCounts.BOUNCED }}
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

        <!-- Email list -->
        <v-card
          class="manager-card"
          rounded="lg"
          variant="flat"
        >
          <v-list
            data-testid="email-manager-table"
            density="comfortable"
          >
            <v-list-item
              v-if="emails.length === 0"
              subtitle="Try adjusting your filters or refresh the page."
              title="No emails found."
            />

            <template
              v-for="email in emails"
              :key="email.id"
            >
              <v-list-item
                :data-testid="`email-row-${email.id}`"
                :aria-expanded="isExpanded(email)"
                :aria-label="`Toggle details for email ${email.id}`"
                :class="['email-row py-2', rowStatusClass(email.deliveryStatus), {'email-row--expanded': isExpanded(email)}]"
                role="button"
                tabindex="0"
                @click="toggleExpanded(email)"
                @keydown.enter.prevent="toggleExpanded(email)"
                @keydown.space.prevent="toggleExpanded(email)"
              >
                <template #prepend>
                  <v-chip
                    class="mr-3 email-type-pill"
                    size="small"
                    variant="tonal"
                  >
                    {{ email.emailType ?? "email" }}
                  </v-chip>
                </template>

                <v-list-item-title class="mb-0">
                  <div class="email-preview">
                    <p class="email-subject">
                      {{ email.subject }}
                    </p>

                    <div class="email-meta-grid">
                      <div class="email-meta-cell">
                        <span class="email-meta-label">Recipient</span>
                        <span class="email-meta-value">{{ email.recipientEmail }}</span>
                      </div>
                      <div class="email-meta-cell">
                        <span class="email-meta-label">Attempts</span>
                        <span class="email-meta-value">{{ email.attempts ?? 0 }}</span>
                      </div>
                      <div class="email-meta-cell">
                        <span class="email-meta-label">{{ email.sentAt ? "Sent at" : "Created at" }}</span>
                        <span class="email-meta-value">{{ formatDateNoSeconds(email.sentAt ?? email.createdAt) }}</span>
                      </div>
                    </div>
                  </div>
                </v-list-item-title>

                <template #append>
                  <div class="email-row-actions">
                    <v-chip
                      :color="statusColor(email.deliveryStatus)"
                      size="small"
                      variant="tonal"
                    >
                      {{ email.deliveryStatus }}
                    </v-chip>

                    <v-btn
                      v-if="canRetry(email)"
                      :data-testid="`email-retry-btn-${email.id}`"
                      :loading="retrying === email.id"
                      size="small"
                      variant="outlined"
                      @click.stop="retryEmail(email)"
                    >
                      Retry
                    </v-btn>
                  </div>
                </template>
              </v-list-item>

              <!-- Expanded detail -->
              <v-expand-transition>
                <div
                  v-if="isExpanded(email)"
                  :data-testid="`email-detail-${email.id}`"
                  class="email-detail px-4 pb-4"
                >
                  <div class="email-detail-grid">
                    <v-sheet
                      class="detail-panel"
                      rounded="md"
                      variant="tonal"
                    >
                      <p class="text-caption text-medium-emphasis mb-2">
                        Recipient
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Name:</strong> {{ email.recipientName ?? "—" }}
                      </p>
                      <p class="text-body-2 mb-0">
                        <strong>Email:</strong> {{ email.recipientEmail }}
                      </p>
                    </v-sheet>

                    <v-sheet
                      class="detail-panel"
                      rounded="md"
                      variant="tonal"
                    >
                      <p class="text-caption text-medium-emphasis mb-2">
                        Delivery
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Status:</strong>
                        <v-chip
                          :color="statusColor(email.deliveryStatus)"
                          class="ml-1"
                          size="x-small"
                          variant="tonal"
                        >
                          {{ email.deliveryStatus }}
                        </v-chip>
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Attempts:</strong> {{ email.attempts ?? 0 }}
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Sent:</strong> {{ formatDate(email.sentAt) }}
                      </p>
                      <p class="text-body-2 mb-1">
                        <strong>Delivered:</strong> {{ formatDate(email.deliveredAt) }}
                      </p>
                      <p
                        v-if="email.openedAt"
                        class="text-body-2 mb-1"
                      >
                        <strong>Opened:</strong> {{ formatDate(email.openedAt) }}
                      </p>
                      <p class="text-body-2 mb-0">
                        <strong>Message ID:</strong>
                        <span class="text-monospace text-caption ml-1">{{ email.messageId ?? "—" }}</span>
                      </p>
                    </v-sheet>
                  </div>

                  <v-sheet
                    v-if="email.jobExecutionId"
                    class="detail-panel mt-3"
                    rounded="md"
                    variant="tonal"
                  >
                    <p class="text-caption text-medium-emphasis mb-2">
                      Linked job
                    </p>
                    <router-link :to="`/management/jobs?search=${email.jobExecutionId}`">
                      Job #{{ email.jobExecutionId }}
                    </router-link>
                  </v-sheet>

                  <v-sheet
                    v-if="email.errorType || email.errorReason"
                    class="detail-panel mt-3"
                    rounded="md"
                    variant="tonal"
                  >
                    <p class="text-caption text-medium-emphasis mb-2">
                      Error detail
                    </p>
                    <p class="text-body-2 text-error mb-0">
                      {{ email.errorType }}: {{ email.errorReason }}
                    </p>
                  </v-sheet>
                </div>
              </v-expand-transition>

              <v-divider class="email-divider mx-4" />
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
              data-testid="email-manager-pagination"
            />
          </div>
        </v-card>
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.email-manager-page {
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

.email-row {
  align-items: flex-start;
  border-left: 3px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s ease, outline-color 0.15s ease;
  outline: 1px solid transparent;
}

.email-row:hover,
.email-row:focus-visible {
  background: rgba(var(--v-theme-on-surface), 0.035);
  outline-color: rgba(var(--v-theme-primary), 0.35);
}

.email-row--expanded {
  background: rgba(var(--v-theme-primary), 0.06);
  outline-color: rgba(var(--v-theme-primary), 0.42);
}

.email-row--success {
  border-left-color: rgba(var(--v-theme-success), 0.7);
}

.email-row--failed {
  border-left-color: rgba(var(--v-theme-error), 0.72);
}

.email-row--sent {
  border-left-color: rgba(var(--v-theme-info), 0.7);
}

.email-row--pending {
  border-left-color: rgba(var(--v-theme-warning), 0.7);
}

.email-type-pill {
  min-height: 34px;
  width: 140px;
  max-width: 140px;
  min-width: 140px;
  padding-inline: 8px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  justify-content: center;
}

.email-type-pill :deep(.v-chip__content) {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.email-preview {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1.6fr);
  gap: 10px 12px;
  align-items: center;
}

.email-subject {
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

.email-meta-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr) minmax(0, 1.55fr);
  gap: 0;
}

.email-meta-cell {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 0 10px;
}

.email-meta-cell + .email-meta-cell {
  border-left: 1px solid rgba(var(--v-theme-success), 0.45);
}

.email-meta-label {
  font-size: 10px;
  line-height: 1.2;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: rgba(var(--v-theme-on-surface), 0.58);
}

.email-meta-value {
  font-size: 13px;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.email-divider {
  border-color: rgba(var(--v-theme-success), 0.45);
  opacity: 1;
}

.email-row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 140px;
}

.email-detail {
  margin-top: 6px;
}

.email-detail-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.detail-panel {
  padding: 12px;
  background: rgba(var(--v-theme-on-surface), 0.025);
}

.manager-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 18px 18px;
}

.stats-cell {
  padding: 4px 8px;
}

@media (max-width: 900px) {
  .email-preview {
    grid-template-columns: 1fr;
  }

  .email-meta-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .email-detail-grid {
    grid-template-columns: 1fr;
  }

  .email-row-actions {
    min-width: 0;
  }

  .manager-pagination {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 640px) {
  .email-meta-grid {
    grid-template-columns: 1fr;
  }

  .email-meta-cell {
    padding: 0;
  }

  .email-meta-cell + .email-meta-cell {
    border-left: 0;
    border-top: 1px solid rgba(var(--v-theme-success), 0.45);
    margin-top: 4px;
    padding-top: 4px;
  }
}
</style>
