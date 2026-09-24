<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import Island from "@/components/island/Island.vue"
import EventForm from "@/components/form/EventForm.vue"
import store from "@/plugins/store"
import {deleteEvent, type EventResponse, readEvent} from "@/domains/events"

const EVENT_LIST = "/events"

const route = useRoute()
const router = useRouter()
const event = ref<EventResponse>()

const isEditing = computed(() => Boolean(route.params.id))

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id) return
  try {
    event.value = await readEvent(id)
  } catch (err) {
    console.error("Error fetching event:", err)
  }
})

const deleting = ref(false)
const working = ref(false)
const failure = ref<string | null>(null)

async function confirmDelete() {
  working.value = true
  failure.value = null
  try {
    await deleteEvent(event.value!.id)
    store.commit("setStatusSnackbarMessage", `Deleted “${event.value!.title}”`)
    deleting.value = false
    await router.replace(EVENT_LIST)
  } catch {
    failure.value = `Couldn't delete “${event.value!.title}”`
  } finally {
    working.value = false
  }
}

/**
 * The page the reader came from, read once on arrival rather than gone back to blindly.
 *
 * `history.state.back` is the entry behind this one, which is the page holding the card they
 * opened, filters and season in its query. Two answers are refused: the login page, which a
 * reader bounced through on the way here has behind them and is the one place saving must not
 * land, and any address outside the spa. Neither leaves anywhere to return to, so both fall back
 * to the list the event is on.
 */
const returnTo = ((): string => {
  const back = router.options.history.state.back
  if (typeof back !== "string" || !back.startsWith("/") || back.startsWith("//")) return EVENT_LIST
  if (back.startsWith("/login")) return EVENT_LIST
  return back
})()

function onSuccess() {
  router.replace(returnTo)
}
</script>

<template>
  <v-main>
    <island
      class="edit-event"
      testid="edit-event-island"
    >
      <header-band>
        <template #head>
          <div class="edit-event__head">
            <div>
              <p class="edit-event__eyebrow">
                {{ isEditing ? event?.title ?? "" : "Organise" }}
              </p>
              <h1 class="edit-event__title">
                {{ isEditing ? "Edit event" : "Add an event" }}
              </h1>
            </div>
            <div
              v-if="isEditing && event"
              class="edit-event__actions"
            >
              <cut-button
                :href="`/events/${event.id}`"
                testid="edit-event-see"
              >
                See the event
              </cut-button>
              <cut-button
                :testid="`event-delete-btn-${event.id}`"
                tone="quiet"
                @click="deleting = true"
              >
                Delete event
              </cut-button>
            </div>
          </div>
        </template>
      </header-band>

      <div class="edit-event__body">
        <event-form
          v-if="!isEditing"
          ref="form"
          :committee-id="Number(route.query.committee) || undefined"
          @cancel="router.replace(returnTo)"
          @submitted="(ok: boolean) => { if (ok) onSuccess() }"
        />
        <event-form
          v-else-if="event"
          ref="form"
          v-model="event"
          @cancel="router.replace(returnTo)"
          @submitted="(ok: boolean) => { if (ok) onSuccess() }"
        />
      </div>

      <confirm-dialog
        v-if="event"
        confirm-label="Delete"
        :failure="failure"
        :open="deleting"
        :question="`Delete “${event.title}”? Its sign-ups go with it, and this can't be undone.`"
        testid="edit-event-delete-dialog"
        title="Delete event"
        :working="working"
        working-label="Deleting"
        @confirm="confirmDelete"
        @update:open="deleting = $event"
      />
    </island>
  </v-main>
</template>

<style scoped>
/* The island root fills a page; the Vuetify main around it already does. */
.edit-event {
  min-height: 0;
}

.edit-event__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem 2rem;
  padding-top: 1.5rem;
}

.edit-event__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.edit-event__title {
  margin-top: 0.7rem;
  font-family: var(--font-display);
  font-size: 3.5rem;
  line-height: 0.95;
  text-transform: uppercase;
}

.edit-event__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.edit-event__body {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto;
  padding: 1.5rem 2rem 3rem;
}

@media (max-width: 767px) {
  .edit-event__head {
    padding-top: 0.5rem;
  }

  .edit-event__title {
    font-size: 2.4rem;
  }

  .edit-event__body {
    padding: 1rem 1.25rem 2.5rem;
  }
}
</style>
