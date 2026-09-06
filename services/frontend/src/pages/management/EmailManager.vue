<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {loadEmailPage, loadEmailStats, readSentEmail, retrySend} from "@/domains/emails/adapters/emails"
import {
  type EmailStats,
  type SentEmail,
  EmailDeliveryStatus,
  canRetry,
  deliveryRate as deliveryRateOf,
  openRate as openRateOf,
  rowStatusClass,
  statusColor,
  statusCounts as countsOf,
  statusOptions as emailStatusOptions,
} from "@/domains/emails"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {usePagedTable, type PageQuery} from "@/composables/usePagedTable"
import store from "@/plugins/store"
import {formatDate, formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "EmailManagerPage"})

const PAGE_SIZE = 50

const stats = ref<EmailStats | null>(null)
const retrying = ref<number | null>(null)
const selectedStatus = ref<EmailDeliveryStatus | "all">("all")

const loadStats = async () => {
  stats.value = await loadEmailStats()
}

// Stats ride along with the rows so the panel and the table describe the same moment, and are
// not awaited: the panel is supplementary and a slow count must not hold the table back.
const loadPage = (query: PageQuery) => {
  void loadStats()
  return loadEmailPage(query, {
    ...(selectedStatus.value !== "all" ? {deliveryStatus: selectedStatus.value} : {}),
  })
}

const table = usePagedTable<SentEmail>(loadPage, {pageSize: PAGE_SIZE})
const {
  rows: emails,
  loading,
  page,
  totalPages,
  totalElements,
  search: searchQuery,
  pageRangeLabel,
  isExpanded,
  toggleExpanded,
  resetToFirstPage,
} = table

watch(selectedStatus, () => {
  resetToFirstPage()
})

// Read once: the list comes from the generated enum, which does not change while the page is open.
const statusOptions = emailStatusOptions()

const deliveryRate = computed(() => deliveryRateOf(stats.value))
const openRate = computed(() => openRateOf(stats.value))
const statusCounts = computed(() => countsOf(emails.value))

const {open: previewOpen, loading: previewLoading, error: previewError, preview, show: showPreview} =
  useEmailPreview()

const openPreview = async (email: SentEmail) => {
  if (email.id == null) return
  await showPreview(() => readSentEmail(email.id as number))
}

const retryEmail = async (email: SentEmail) => {
  if (email.id == null) return
  retrying.value = email.id
  try {
    const sent = await retrySend(email.id)
    // Used to fall into an empty catch, so a refused retry looked exactly like a successful one.
    if (!sent.ok) {
      store.commit("setStatusSnackbarMessage", sent.reason)
      return
    }
    await table.refresh()
  } finally {
    retrying.value = null
  }
}

onMounted(async () => {
  await table.refresh()
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
              @click="resetToFirstPage"
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
                      v-if="email.previewable"
                      :aria-label="`Preview email ${email.id}`"
                      :data-testid="`email-preview-btn-${email.id}`"
                      size="small"
                      variant="outlined"
                      @click.stop="openPreview(email)"
                    >
                      Preview
                    </v-btn>

                    <v-btn
                      v-if="canRetry(email)"
                      :data-testid="`email-retry-btn-${email.id}`"
                      :disabled="retrying === email.id"
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
    <email-preview-dialog
      v-model="previewOpen"
      :error="previewError"
      :loading="previewLoading"
      :preview="preview"
      title="Sent email"
    />
  </v-main>
</template>

<style lang="scss" scoped>
.email-manager-page {
  max-width: 980px;
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
