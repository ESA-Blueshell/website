<template>
  <v-dialog
    :model-value="eventToDelete"
    max-width="400"
  >
    <template #activator="{ props: dialog }">
      <v-list>
        <div
          v-for="(event, i) in events"
          :key="event.title + event.startTime"
        >
          <event-list-item :event="event">
            <template #append>
              <p
                v-if="display.smAndUp"
                class="ml-4"
              >
                {{ idToCommittee[event.committee] }}
              </p>

              <div
                style="display: grid"
                class="mx-4"
              >
                <v-tooltip
                  location="left"
                  text="Check signups"
                >
                  <template #activator="{ props }">
                    <v-btn
                      icon="mdi-list-status"
                      variant="plain"
                      :disabled="!event.signUp"
                      v-bind="props"
                      @click="router.push('signups/' + event.id)"
                    />
                  </template>
                </v-tooltip>

                <v-tooltip
                  location="left"
                  text="Edit event"
                >
                  <template #activator="{ props }">
                    <v-btn
                      icon="mdi-pencil"
                      variant="plain"
                      v-bind="props"
                      @click="router.push('edit/' + event.id)"
                    />
                  </template>
                </v-tooltip>

                <v-tooltip
                  location="left"
                  text="Delete event"
                >
                  <template #activator="{ props: tooltip }">
                    <v-btn
                      icon="mdi-delete"
                      variant="plain"
                      v-bind="{ ...tooltip, ...dialog }"
                      @click="eventToDelete = event"
                    />
                  </template>
                </v-tooltip>
              </div>
            </template>
          </event-list-item>

          <v-divider
            v-if="i < events.length - 1"
            :key="i"
          />
        </div>
      </v-list>
    </template>

    <v-card>
      <v-card-title>
        <p class="text-h5">
          Are you sure you want to delete this event:
          {{ eventToDelete ? eventToDelete.title : 'NO EVENT????' }}
        </p>
      </v-card-title>
      <v-card-text>
        There will be no undo
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          variant="text"
          @click="eventToDelete = null"
        >
          No
        </v-btn>
        <v-btn
          color="error"
          variant="text"
          @click="deleteEvent"
        >
          Yes
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {EventService} from "@/services";
import type {EventModel} from "@/models"
import EventListItem from '@/components/events/EventListItem.vue'
import {useDisplay} from "vuetify";

defineOptions({name: 'EventManageList'})
const display = useDisplay()


const props = defineProps<{
  initialEvents: EventModel[],
  idToCommittee,
}>()

const events = ref<EventModel[]>(props.initialEvents)
const eventToDelete = ref<EventModel>()
const eventService = new EventService()

// Access router and store if you still need them
const router = useRouter()

function deleteEvent(): void {
  if (!eventToDelete.value) return

  eventService.deleteEvent(eventToDelete.value.id as number)
    .then(() => {
      events.value = events.value.filter((e) => e.id !== eventToDelete.value?.id);
      eventToDelete.value = undefined
    })
}
</script>

<style scoped>
/* Your styles here if needed */
</style>
