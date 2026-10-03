<script lang="ts" setup>
/* The events waiting for the board, new ones and re-approvals apart, soonest first. A re-approval
   says what changed since it was approved. Approving here is the event page's own approving: the
   same call, and the same question of when its events-info post goes out. */
import {computed, onMounted, ref} from "vue"
import AnnounceDialog from "@/domains/events/island/AnnounceDialog.vue"
import {type QueuedEvent, changesSaid, readApprovalQueue, setEventApproved, useAnnouncePrompt, whenOf} from "@/domains/events"
import store from "@/plugins/store"

defineOptions({name: "EventQueuePage"})

const queue = ref<QueuedEvent[] | null>(null)
const loaded = ref(false)
const acting = ref<number | null>(null)

const fresh = computed(() => queue.value?.filter((one) => !one.reapproval) ?? [])
const changed = computed(() => queue.value?.filter((one) => one.reapproval) ?? [])
const groups = computed(() => [
  {key: "new", title: "New", rows: fresh.value},
  {key: "reapproval", title: "Changed since approval", rows: changed.value},
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
  <div
    class="queue"
    data-testid="event-queue"
  >
    <header class="queue__head">
      <p class="queue__eyebrow">
        Content
      </p>
      <h1 class="queue__title">
        Events to approve
      </h1>
      <p class="queue__note">
        Events a committee made or changed, waiting for the board. Approving posts them on Discord as approving on the event page does.
      </p>
    </header>

    <p
      v-if="loaded && queue == null"
      class="queue__note"
      data-testid="event-queue-unreadable"
    >
      The queue could not be read. Try again in a moment.
    </p>
    <p
      v-else-if="loaded && queue?.length === 0"
      class="queue__note"
      data-testid="event-queue-empty"
    >
      Nothing is waiting for the board.
    </p>

    <template
      v-for="group in groups"
      :key="group.key"
    >
      <section
        v-if="group.rows.length"
        class="queue__group"
        :data-testid="`event-queue-${group.key}`"
      >
        <p class="queue__folder">
          <span>{{ group.title }}</span>
          <span class="queue__sub">{{ group.rows.length }}</span>
        </p>
        <ul class="queue__rows">
          <li
            v-for="queued in group.rows"
            :key="queued.event.id"
            class="queue__row"
            :data-testid="`event-queue-row-${queued.event.id}`"
          >
            <span class="queue__name">{{ queued.event.title }}</span>
            <span class="queue__sub">{{ whenOf(queued.event).day }} · {{ whenOf(queued.event).hours }}</span>
            <span
              class="queue__sub queue__changes"
              :data-testid="`event-queue-changes-${queued.event.id}`"
            >{{ queued.reapproval ? changesSaid(queued.changes) : "" }}</span>
            <span class="queue__acts">
              <button
                class="queue__mini queue__mini--main"
                :data-testid="`event-queue-approve-${queued.event.id}`"
                :disabled="acting != null"
                type="button"
                @click="approve(queued)"
              >
                Approve
              </button>
              <router-link
                class="queue__mini"
                :data-testid="`event-queue-open-${queued.event.id}`"
                :to="`/events/${queued.event.id}`"
              >
                Open
              </router-link>
            </span>
          </li>
        </ul>
      </section>
    </template>

    <announce-dialog
      :later="announceLater"
      :open="announceOpen"
      @answer="answerAnnounce"
    />
  </div>
</template>

<style scoped>
.queue {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.queue__head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.queue__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.queue__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.queue__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.queue__group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.queue__folder {
  display: flex;
  align-items: baseline;
  gap: 0.8rem;
  margin: 0.8rem 0 0;
  font-weight: 600;
}

.queue__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.queue__row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) minmax(0, 1.2fr) auto;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.queue__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.queue__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue__changes {
  white-space: normal;
}

.queue__acts {
  display: flex;
  gap: 0.4rem;
}

.queue__mini {
  padding: 0.25rem 0.6rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.8rem;
  color: var(--color-chalk);
  text-decoration: none;
  cursor: pointer;
}

.queue__mini--main {
  border-color: var(--color-brand);
}

.queue__mini:disabled {
  opacity: 0.45;
  cursor: default;
}

@media (max-width: 839px) {
  .queue {
    padding: 1.2rem 1.1rem 2rem;
  }

  .queue__row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
