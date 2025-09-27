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
                {{ committeeForEvent(event) }}
              </p>

              <div
                class="mx-4"
                style="display: grid"
              >
                <v-tooltip
                  location="left"
                  text="Check signups"
                >
                  <template #activator="{ props }">
                    <v-btn
                      :disabled="!event.signUp"
                      icon="mdi-list-status"
                      v-bind="props"
                      variant="plain"
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
                      v-bind="props"
                      variant="plain"
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
                      v-bind="{ ...tooltip, ...dialog }"
                      variant="plain"
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
          {{ eventToDelete ? eventToDelete.title : "NO EVENT????" }}
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

<script lang="ts" setup>
import {computed, ref, toRef, watch} from "vue"
import {useRouter} from "vue-router"
import EventListItem from "@/components/events/EventListItem.vue"
import {useDisplay} from "vuetify"
import {type AdvancedCommittee, deleteEventById, type Event, type SimpleCommittee} from "@/lib"

defineOptions({name: "EventManageList"})
const display = useDisplay()


const props = defineProps<{
  initialEvents: Event[],
  initialCommittees: AdvancedCommittee[],
}>()

const events = ref<Event[]>(props.initialEvents)
const eventToDelete = ref<Event>()

const committees = computed<AdvancedCommittee[]>(() => props.initialCommittees ?? [])

const committeesRef = toRef(props, "initialCommittees")

watch(
  committeesRef,
  (newVal) => {
    console.log("committees changed:", newVal)
  },
  {deep: true, immediate: true},
)

// Access router and store if you still need them
const router = useRouter()

function committeeForEvent(event: Event) {
  console.log("committee for event:", event)
  console.log("committees:", committees.value)
  if (!event.committeeId) return "No committee"
  return committees.value.find((c: SimpleCommittee) => c.id === event.committeeId)
}

async function deleteEvent() {
  if (!eventToDelete.value) return

  try {
    await deleteEventById({
      path: {
        eventId: eventToDelete.value.id as number,
      },
    })

    events.value = events.value.filter((e) => e.id !== eventToDelete.value?.id)
    eventToDelete.value = undefined
  } catch (e) {
    console.error(e)
  }
}
</script>

<style lang="scss" scoped>
/* Your styles here if needed */
</style>
