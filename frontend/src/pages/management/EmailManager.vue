<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {getStats1, list1, retry1, type EmailOutbox, type EmailOutboxStats} from "@/services/api"

type EmailDeliveryStatus = NonNullable<EmailOutbox["deliveryStatus"]>

defineOptions({name: "EmailManagerPage"})

const PAGE_SIZE = 50

const emails = ref<EmailOutbox[]>([])
const loading = ref(false)
const retrying = ref<number | null>(null)
const stats = ref<EmailOutboxStats | null>(null)
const selectedStatus = ref<EmailDeliveryStatus | "all">("all")
const selectedEmailType = ref<string>("all")
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


const pageRangeLabel = computed<string>(() => {
  if (totalElements.value === 0 || emails.value.length === 0) return `0 of ${totalElements.value}`
  const start = (page.value - 1) * PAGE_SIZE + 1
  const end = Math.min(start + emails.value.length - 1, totalElements.value)
  return `${start}-${end} of ${totalElements.value}`
})

const isExpanded = (email: EmailOutbox): boolean => {
  if (email.id == null) return false
  return expandedRows.value.includes(email.id)
}

const toggleExpanded = (email: EmailOutbox) => {
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

const statusColor = (status?: EmailDeliveryStatus): string => {
  if (status === "DELIVERED" || status === "OPENED") return "success"
  if (status === "FAILED" || status === "BOUNCED") return "error"
  if (status === "SENT") return "info"
  if (status === "PENDING") return "warning"
  return "secondary"
}

const canRetry = (email: EmailOutbox): boolean => {
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

watch([selectedStatus, selectedEmailType], () => {
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
  } catch (e) {
    $handleNetworkError(e)
  }
}

const refresh = async () => {
  loading.value = true
  try {
    const response = await list1({
      query: {
        page: page.value - 1,
        size: PAGE_SIZE,
        sort: ["createdAt,desc"],
        deliveryStatus: selectedStatus.value !== "all" ? selectedStatus.value : undefined,
        emailType: selectedEmailType.value !== "all" ? selectedEmailType.value : undefined,
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

const retryEmail = async (email: EmailOutbox) => {
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
  <TopBanner title="Manage emails" />

  <v-container>
    <!-- Stats panel -->
    <v-row
      class="mb-4"
      dense
    >
      <v-col
        cols="12"
        sm="6"
        md="4"
        lg="2"
      >
        <v-card
          variant="outlined"
          class="text-center pa-3"
          data-testid="email-stats-total"
        >
          <div class="text-h5 font-weight-bold">
            {{ stats?.totalCount ?? "—" }}
          </div>
          <div class="text-caption text-medium-emphasis">
            Total
          </div>
        </v-card>
      </v-col>
      <v-col
        cols="12"
        sm="6"
        md="4"
        lg="2"
      >
        <v-card
          variant="outlined"
          class="text-center pa-3"
          color="info"
          data-testid="email-stats-sent"
        >
          <div class="text-h5 font-weight-bold">
            {{ stats?.sentCount ?? "—" }}
          </div>
          <div class="text-caption">
            Sent
          </div>
        </v-card>
      </v-col>
      <v-col
        cols="12"
        sm="6"
        md="4"
        lg="2"
      >
        <v-card
          variant="outlined"
          class="text-center pa-3"
          color="success"
          data-testid="email-stats-delivered"
        >
          <div class="text-h5 font-weight-bold">
            {{ stats?.deliveredCount ?? "—" }}
          </div>
          <div class="text-caption">
            Delivered ({{ deliveryRate }}%)
          </div>
        </v-card>
      </v-col>
      <v-col
        cols="12"
        sm="6"
        md="4"
        lg="2"
      >
        <v-card
          variant="outlined"
          class="text-center pa-3"
          color="success"
          data-testid="email-stats-opened"
        >
          <div class="text-h5 font-weight-bold">
            {{ stats?.openedCount ?? "—" }}
          </div>
          <div class="text-caption">
            Opened ({{ openRate }}%)
          </div>
        </v-card>
      </v-col>
      <v-col
        cols="12"
        sm="6"
        md="4"
        lg="2"
      >
        <v-card
          variant="outlined"
          class="text-center pa-3"
          color="error"
          data-testid="email-stats-bounced"
        >
          <div class="text-h5 font-weight-bold">
            {{ stats?.bouncedCount ?? "—" }}
          </div>
          <div class="text-caption">
            Bounced
          </div>
        </v-card>
      </v-col>
      <v-col
        cols="12"
        sm="6"
        md="4"
        lg="2"
      >
        <v-card
          variant="outlined"
          class="text-center pa-3"
          color="error"
          data-testid="email-stats-failed"
        >
          <div class="text-h5 font-weight-bold">
            {{ stats?.failedCount ?? "—" }}
          </div>
          <div class="text-caption">
            Failed
          </div>
        </v-card>
      </v-col>
    </v-row>

    <!-- Filter card -->
    <v-card
      class="mb-4"
      variant="outlined"
    >
      <v-card-text>
        <v-row dense>
          <v-col
            cols="12"
            sm="5"
          >
            <v-text-field
              v-model="searchQuery"
              label="Search recipient or subject"
              prepend-inner-icon="mdi-magnify"
              clearable
              hide-details
              density="compact"
            />
          </v-col>
          <v-col
            cols="12"
            sm="4"
          >
            <v-select
              v-model="selectedStatus"
              :items="statusOptions"
              item-title="title"
              item-value="value"
              label="Status"
              hide-details
              density="compact"
            />
          </v-col>
          <v-col
            cols="12"
            sm="3"
            class="d-flex align-center"
          >
            <v-btn
              variant="tonal"
              @click="resetToFirstPageAndRefresh"
            >
              Refresh
            </v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Email list -->
    <v-card variant="outlined">
      <v-card-text class="pa-0">
        <div
          v-if="loading"
          class="pa-8 text-center"
        >
          <v-progress-circular indeterminate />
        </div>
        <div
          v-else-if="emails.length === 0"
          class="pa-8 text-center text-medium-emphasis"
        >
          No emails found.
        </div>
        <v-list
          v-else
          lines="two"
        >
          <template
            v-for="email in emails"
            :key="email.id"
          >
            <v-list-item
              :class="{'bg-error-lighten': email.deliveryStatus === 'FAILED' || email.deliveryStatus === 'BOUNCED'}"
              @click="toggleExpanded(email)"
            >
              <template #prepend>
                <v-chip
                  :color="statusColor(email.deliveryStatus)"
                  size="small"
                  class="mr-3"
                >
                  {{ email.deliveryStatus }}
                </v-chip>
              </template>

              <v-list-item-title
                class="text-truncate"
                style="max-width: 400px"
              >
                {{ email.subject }}
              </v-list-item-title>
              <v-list-item-subtitle>
                {{ email.recipientEmail }} · {{ email.emailType }} · {{ formatDate(email.sentAt ?? email.createdAt) }}
              </v-list-item-subtitle>

              <template #append>
                <v-icon>{{ isExpanded(email) ? "mdi-chevron-up" : "mdi-chevron-down" }}</v-icon>
              </template>
            </v-list-item>

            <!-- Expanded detail -->
            <v-expand-transition>
              <div v-if="isExpanded(email)">
                <v-card
                  class="mx-4 mb-3"
                  variant="tonal"
                >
                  <v-card-text>
                    <v-row dense>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Recipient
                        </div>
                        <div>{{ email.recipientName }} &lt;{{ email.recipientEmail }}&gt;</div>
                      </v-col>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Subject
                        </div>
                        <div>{{ email.subject }}</div>
                      </v-col>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Message ID
                        </div>
                        <div class="text-monospace text-body-2">
                          {{ email.messageId ?? "—" }}
                        </div>
                      </v-col>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Attempts
                        </div>
                        <div>{{ email.attempts ?? 0 }}</div>
                      </v-col>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Sent at
                        </div>
                        <div>{{ formatDate(email.sentAt) }}</div>
                      </v-col>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Delivered at
                        </div>
                        <div>{{ formatDate(email.deliveredAt) }}</div>
                      </v-col>
                      <v-col
                        v-if="email.openedAt"
                        cols="12"
                        sm="6"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Opened at
                        </div>
                        <div>{{ formatDate(email.openedAt) }}</div>
                      </v-col>
                      <v-col
                        v-if="email.jobExecutionId"
                        cols="12"
                        sm="6"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Linked job
                        </div>
                        <router-link :to="`/management/jobs?search=${email.jobExecutionId}`">
                          Job #{{ email.jobExecutionId }}
                        </router-link>
                      </v-col>
                      <v-col
                        v-if="email.errorReason || email.errorType"
                        cols="12"
                      >
                        <div class="text-caption text-medium-emphasis">
                          Error
                        </div>
                        <div class="text-error text-body-2">
                          {{ email.errorType }}: {{ email.errorReason }}
                        </div>
                      </v-col>
                    </v-row>
                    <v-row
                      v-if="canRetry(email)"
                      dense
                      class="mt-2"
                    >
                      <v-col>
                        <v-btn
                          color="primary"
                          size="small"
                          :loading="retrying === email.id"
                          @click.stop="retryEmail(email)"
                        >
                          Retry
                        </v-btn>
                      </v-col>
                    </v-row>
                  </v-card-text>
                </v-card>
              </div>
            </v-expand-transition>

            <v-divider />
          </template>
        </v-list>
      </v-card-text>

      <!-- Pagination -->
      <v-card-actions
        v-if="totalElements > 0"
        class="justify-space-between px-4 py-2"
      >
        <span class="text-body-2 text-medium-emphasis">{{ pageRangeLabel }}</span>
        <v-pagination
          v-model="page"
          :length="totalPages"
          :total-visible="5"
          density="compact"
        />
      </v-card-actions>
    </v-card>
  </v-container>
</template>
