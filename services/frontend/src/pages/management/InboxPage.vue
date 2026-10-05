<script lang="ts" setup>
/* Mail to an @esa-blueshell.nl address with no mailbox of its own, replies to the site's emails
   among it, newest first. Bounces show on the email that bounced, not here. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {emailTypeLabel} from "@/domains/emails"
import {type InboxCounts, type InboxEntry, InboxState, followsOf, inboxStateWord, loadInboxPage, readInboxCounts} from "@/domains/mail"
import {usePagedTable} from "@/composables/usePagedTable"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "InboxPage"})

const counts = ref<InboxCounts | null>(null)

const table = usePagedTable<InboxEntry>((query) => {
  void readInboxCounts().then((read) => {
    counts.value = read
  })
  return loadInboxPage(query)
}, {pageSize: 50})
const {rows, search, pageRangeLabel, refresh, more, sortKey, descending, sortBy} = table

// The inbox is read a page at a time, so the server orders it.
const COLUMNS: TableColumn[] = [
  {key: "received", label: "Received", sortable: true},
  {key: "from", label: "From", wrap: true, sortable: true},
  {key: "what", label: "Subject", wrap: true, sortable: true},
  {key: "state", label: "State", sortable: true},
  {key: "by", label: "Handled by"},
]

const oldest = computed(() => (counts.value?.oldestNewAt ? `Oldest from ${formatMoment(counts.value.oldestNewAt)}` : "Nothing waits"))
const facts = computed(() => [
  {label: "New", value: String(counts.value?.new ?? 0), sub: oldest.value, testid: "inbox-new"},
  {label: "Replied or handled", value: String(counts.value?.done ?? 0), sub: "Since the inbox started", testid: "inbox-done"},
  {label: "Automatic replies", value: String(counts.value?.automatic ?? 0), sub: "Kept apart from what needs an answer", testid: "inbox-automatic"},
])
const senderOf = (entry: InboxEntry) => entry.senderName ?? entry.fromName ?? entry.fromAddress
const markOf = (entry: InboxEntry) => (entry.automatic ? "not-compared" : entry.state === InboxState.NEW ? "not-created" : "in-step")
const follows = (entry: InboxEntry) => followsOf(entry, emailTypeLabel)

onMounted(refresh)
</script>

<template>
  <management-page
    eyebrow="Mail"
    testid="inbox"
    title="Inbox"
  >
    <template #lede>
      Mail sent to an @esa-blueshell.nl address that has no mailbox of its own, including replies to the site's emails.
      Bounces are not here: they show on the email that bounced.
    </template>
    <template #actions>
      <cut-button
        testid="inbox-refresh"
        tone="quiet"
        @click="refresh"
      >
        Refresh
      </cut-button>
    </template>

    <fact-list
      class="inbox__facts"
      data-testid="inbox-facts"
      :facts="facts"
    />

    <management-table
      :columns="COLUMNS"
      :descending="descending"
      :row-key="(entry) => entry.id"
      :row-testid="(entry) => `inbox-row-${entry.id}`"
      :rows="rows"
      :sort-key="sortKey"
      testid="inbox-table"
      :to="(entry) => `/management/mail/inbox/${entry.id}`"
      @more="more"
      @sort="sortBy"
    >
      <template #count>
        {{ pageRangeLabel }}
      </template>
      <template #search>
        <search-box
          label="Search the inbox"
          :model-value="search ?? ''"
          testid="inbox-search"
          @update:model-value="search = $event"
        />
      </template>
      <template #empty>
        <span data-testid="inbox-empty">Nothing has arrived.</span>
      </template>
      <template #received="{row}">
        {{ formatMoment(row.receivedAt) }}
      </template>
      <template #from="{row}">
        <router-link
          v-if="row.senderUserId"
          class="inbox__who"
          :to="`/management/users/${row.senderUserId}`"
        >
          {{ senderOf(row) }}
        </router-link>
        <span v-else>{{ row.fromName ?? "Unknown sender" }}</span>
        <span class="mg-sub">{{ row.fromAddress }}</span>
      </template>
      <template #what="{row}">
        <router-link
          class="inbox__subject"
          :data-testid="`inbox-open-${row.id}`"
          :to="`/management/mail/inbox/${row.id}`"
        >
          {{ row.subject || "(no subject)" }}
        </router-link>
        <span
          v-if="follows(row)"
          class="mg-sub"
          :data-testid="`inbox-follows-${row.id}`"
        >{{ follows(row) }}</span>
      </template>
      <template #state="{row}">
        <state-mark
          :kind="markOf(row)"
          :testid="`inbox-state-${row.id}`"
        >
          {{ inboxStateWord(row) }}
        </state-mark>
      </template>
      <template #by="{row}">
        <span :class="{'mg-quiet': !row.handledByName}">{{ row.handledByName ?? "·" }}</span>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${senderOf(row)} · ${formatMoment(row.receivedAt)}`"
          :name="row.subject || '(no subject)'"
          :testid="`inbox-row-${row.id}`"
          :to="`/management/mail/inbox/${row.id}`"
        >
          <state-mark
            :kind="markOf(row)"
            :testid="`inbox-state-${row.id}`"
          >
            {{ inboxStateWord(row) }}
          </state-mark>
        </management-row>
      </template>
    </management-table>
  </management-page>
</template>

<style scoped>
.inbox__facts {
  padding: 1.1rem 0 1.2rem;
}

.inbox__subject {
  font-weight: 600;
  color: var(--color-chalk);
}

.inbox__who {
  color: var(--color-chalk);
}

.inbox__subject:hover,
.inbox__who:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
