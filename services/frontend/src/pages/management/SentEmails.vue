<script lang="ts" setup>
/* Every email the site sends, newest first, from the moment its job is queued. A failed one is
   retried as it was; any other is resent from the person's current address. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {
  type EmailStats,
  type SentEmail,
  canResend,
  canRetry,
  emailTypeLabel,
  loadEmailPage,
  loadEmailStats,
  resendEmail,
  retrySend,
  sentFacts,
  stateKindOf,
  statusWord,
} from "@/domains/emails"
import {usePagedTable} from "@/composables/usePagedTable"
import store from "@/plugins/store"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "SentEmailsPage"})

const stats = ref<EmailStats | null>(null)
const acting = ref<number | null>(null)

const loadStats = async () => {
  stats.value = await loadEmailStats()
}

const table = usePagedTable<SentEmail>((query) => {
  void loadStats()
  return loadEmailPage(query)
}, {pageSize: 50})
const {rows, search, pageRangeLabel, refresh, more, sortKey, descending, sortBy} = table

// Sent mail is read a page at a time, so the server orders it.
const COLUMNS: TableColumn[] = [
  {key: "when", label: "Sent", sortable: true},
  {key: "what", label: "Email", wrap: true, sortable: true},
  {key: "to", label: "To", sortable: true},
  {key: "status", label: "Status", sortable: true},
  {key: "attempts", label: "Attempts", sortable: true},
]

const facts = computed(() => sentFacts(stats.value))
const factRows = computed(() => [
  {label: "Queued", value: String(facts.value.queued), sub: "Waiting for their job to send them", testid: "sent-emails-queued"},
  {label: "Delivered", value: `${facts.value.delivered}%`, sub: `Of ${facts.value.sent} sent, all time; ${facts.value.opened}% opened`, testid: "sent-emails-delivered"},
  {label: "Needs a look", value: String(facts.value.needsLook), sub: `${facts.value.bounced} bounced, ${facts.value.failed} failed`, testid: "sent-emails-needs-look"},
])

/** Retry sends this email again; Resend makes a new one for the person's current address. */
const act = async (email: SentEmail, how: "retry" | "resend") => {
  if (email.id == null || acting.value !== null) return
  acting.value = email.id
  try {
    const answered = how === "retry" ? await retrySend(email.id) : await resendEmail(email.id)
    if (!answered.ok) {
      store.commit("setStatusSnackbarMessage", answered.reason)
      return
    }
    await refresh()
  } finally {
    acting.value = null
  }
}

onMounted(refresh)
</script>

<template>
  <management-page
    eyebrow="Mail"
    testid="sent-emails"
    title="Sent"
  >
    <template #lede>
      Every email the site sends, from the moment it is queued, and whether it arrived.
    </template>
    <template #actions>
      <cut-button
        href="/management/mail/write"
        testid="sent-emails-write"
        tone="solid"
      >
        Write an email
      </cut-button>
      <cut-button
        testid="sent-emails-refresh"
        tone="quiet"
        @click="refresh"
      >
        Refresh
      </cut-button>
    </template>

    <fact-list
      class="sent__facts"
      data-testid="sent-emails-facts"
      :facts="factRows"
    />

    <management-table
      :columns="COLUMNS"
      :descending="descending"
      :row-key="(email) => email.id ?? 0"
      :row-testid="(email) => `sent-email-row-${email.id}`"
      :rows="rows"
      :sort-key="sortKey"
      testid="sent-emails-table"
      :to="(email) => `/management/mail/sent/${email.id}`"
      @more="more"
      @sort="sortBy"
    >
      <template #count>
        {{ pageRangeLabel }}
      </template>
      <template #search>
        <search-box
          label="Search emails"
          :model-value="search ?? ''"
          testid="sent-emails-search"
          @update:model-value="search = $event"
        />
      </template>
      <template #empty>
        <span data-testid="sent-emails-empty">No emails to show.</span>
      </template>
      <template #when="{row}">
        {{ formatMoment(row.sentAt ?? row.createdAt) }}
      </template>
      <template #what="{row}">
        <router-link
          class="sent__subject"
          :data-testid="`sent-email-open-${row.id}`"
          :to="`/management/mail/sent/${row.id}`"
        >
          {{ row.subject }}
        </router-link>
        <span class="mg-sub">{{ emailTypeLabel(row.emailType) }}{{ row.senderAddress ? ` · from ${row.senderAddress}` : "" }}</span>
      </template>
      <template #to="{row}">
        {{ row.recipientEmail }}
      </template>
      <template #status="{row}">
        <state-mark
          :kind="stateKindOf(row.deliveryStatus)"
          :testid="`sent-email-status-${row.id}`"
        >
          {{ statusWord(row.deliveryStatus) }}
        </state-mark>
      </template>
      <template #attempts="{row}">
        {{ row.attempts ?? 0 }}
      </template>
      <template #acts="{row}">
        <mini-button
          v-if="canRetry(row)"
          :disabled="acting !== null"
          :testid="`sent-email-retry-${row.id}`"
          @click="act(row, 'retry')"
        >
          Retry
        </mini-button>
        <mini-button
          v-else-if="canResend(row)"
          :disabled="acting !== null"
          :testid="`sent-email-resend-${row.id}`"
          @click="act(row, 'resend')"
        >
          Resend
        </mini-button>
        <span
          v-else-if="row.deliveryStatus === 'QUEUED'"
          class="mg-quiet"
        >Job waiting</span>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${row.recipientEmail} · ${formatMoment(row.sentAt ?? row.createdAt)}`"
          :name="row.subject ?? ''"
          :testid="`sent-email-row-${row.id}`"
          :to="`/management/mail/sent/${row.id}`"
        >
          <state-mark
            :kind="stateKindOf(row.deliveryStatus)"
            :testid="`sent-email-status-${row.id}`"
          >
            {{ statusWord(row.deliveryStatus) }}
          </state-mark>
        </management-row>
      </template>
    </management-table>
  </management-page>
</template>

<style scoped>
.sent__facts {
  padding: 1.1rem 0 1.2rem;
}

.sent__subject {
  font-weight: 600;
  color: var(--color-chalk);
}

.sent__subject:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
