<script lang="ts" setup>
/* The events waiting for the board, new ones and re-approvals apart, soonest first. A re-approval
   says what changed since it was approved. Approving here is the event page's own approving: the
   same call, and the same question of when its events-info post goes out. */
import {computed, onMounted, ref} from "vue"
import FactList from "@/components/island/FactList.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import AnnounceDialog from "@/domains/events/island/AnnounceDialog.vue"
import {type QueuedEvent, changesSaid, readApprovalQueue, setEventApproved, useAnnouncePrompt, whenOf} from "@/domains/events"
import store from "@/plugins/store"

defineOptions({name: "EventQueuePage"})

const queue = ref<QueuedEvent[] | null>(null)
const loaded = ref(false)
const acting = ref<number | null>(null)

const fresh = computed(() => queue.value?.filter((one) => !one.reapproval) ?? [])
const changed = computed(() => queue.value?.filter((one) => one.reapproval) ?? [])
const COLUMNS: TableColumn[] = [
  {key: "event", label: "Event", wrap: true},
  {key: "when", label: "When"},
  {key: "state", label: "State"},
  {key: "changed", label: "Changed", wrap: true},
]

// New events first, then the ones changed since they were approved.
const rows = computed(() => [...fresh.value, ...changed.value])

const facts = computed(() => [
  {label: "Awaiting", value: String(rows.value.length), sub: `${fresh.value.length} new, ${changed.value.length} changed since approval`},
  {label: "New", value: String(fresh.value.length), sub: "Never listed yet"},
  {label: "Changed since approval", value: String(changed.value.length), sub: "Their Discord posts stay as last approved"},
])

const load = async () => {
  queue.value = await readApprovalQueue()
  loaded.value = true
}

const {open: announceOpen, later: announceLater, ask: askAnnounce, answer: answerAnnounce} = useAnnouncePrompt()

const approve = async (queued: QueuedEvent) => {
  if (acting.value != null) return
  const announce = await askAnnounce(queued.event)
  if (announce === null) return
  acting.value = queued.event.id
  try {
    await setEventApproved(queued.event.id, true, announce)
    store.commit("setStatusSnackbarMessage", `${queued.event.title} is approved.`)
    await load()
  } catch {
    store.commit("setStatusSnackbarMessage", `${queued.event.title} could not be approved.`)
  } finally {
    acting.value = null
  }
}

onMounted(load)
</script>

<template>
  <management-page
    eyebrow="Content"
    testid="event-queue"
    title="Events to approve"
  >
    <template #lede>
      Events a committee made or changed, waiting for the board. Approving posts them on Discord as approving on the
      event page does.
    </template>

    <fact-list
      class="queue__facts"
      :facts="facts"
    />

    <p
      v-if="loaded && queue == null"
      class="queue__note"
      data-testid="event-queue-unreadable"
    >
      The queue could not be read. Try again in a moment.
    </p>

    <management-table
      v-else
      :columns="COLUMNS"
      :row-key="(queued) => queued.event.id"
      :row-testid="(queued) => `event-queue-row-${queued.event.id}`"
      :rows="rows"
      testid="event-queue-table"
      :to="(queued) => `/events/${queued.event.id}`"
    >
      <template
        v-if="loaded"
        #empty
      >
        <span data-testid="event-queue-empty">Nothing is waiting for the board.</span>
      </template>
      <template #event="{row}">
        <router-link
          class="mg-name"
          :to="`/events/${row.event.id}`"
        >
          {{ row.event.title }}
        </router-link>
      </template>
      <template #when="{row}">
        {{ whenOf(row.event).day }} · {{ whenOf(row.event).hours }}
      </template>
      <template #state="{row}">
        <state-mark
          kind="extra"
          :testid="`event-queue-${row.reapproval ? 'reapproval' : 'new'}-${row.event.id}`"
        >
          {{ row.reapproval ? "Awaiting re-approval" : "Awaiting approval" }}
        </state-mark>
      </template>
      <template #changed="{row}">
        <span
          :class="{'mg-quiet': !row.reapproval}"
          :data-testid="`event-queue-changes-${row.event.id}`"
        >{{ row.reapproval ? changesSaid(row.changes) : "New" }}</span>
      </template>
      <template #acts="{row}">
        <mini-button
          :disabled="acting != null"
          :testid="`event-queue-approve-${row.event.id}`"
          @click="approve(row)"
        >
          Approve
        </mini-button>
        <mini-button
          :testid="`event-queue-open-${row.event.id}`"
          :to="`/events/${row.event.id}`"
        >
          Open
        </mini-button>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${whenOf(row.event).day} · ${whenOf(row.event).hours}`"
          :name="row.event.title"
          :testid="`event-queue-row-${row.event.id}`"
        >
          <state-mark kind="extra">
            {{ row.reapproval ? changesSaid(row.changes) : "Awaiting approval" }}
          </state-mark>
          <template #acts>
            <mini-button
              :disabled="acting != null"
              :testid="`event-queue-approve-${row.event.id}`"
              @click="approve(row)"
            >
              Approve
            </mini-button>
            <mini-button
              :testid="`event-queue-open-${row.event.id}`"
              :to="`/events/${row.event.id}`"
            >
              Open
            </mini-button>
          </template>
        </management-row>
      </template>
    </management-table>

    <announce-dialog
      :later="announceLater"
      :open="announceOpen"
      @answer="answerAnnounce"
    />
  </management-page>
</template>

<style scoped>
.queue__facts {
  padding: 1.1rem 0 1.2rem;
}

.queue__note {
  color: var(--color-ash);
}
</style>
