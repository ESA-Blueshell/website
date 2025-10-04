<script lang="ts" setup>
import $markdownToHtml from "@/plugins/markdownToHtml.ts"
import {computed, nextTick, onMounted, ref} from "vue"
import {createEvent as createIcsEvent} from "ics"
import store from "@/plugins/store.ts"
import {$goto} from "@/plugins/goto"
import {useRoute, useRouter} from "vue-router"
import {useTheme} from "vuetify"
import {DateTime} from "luxon"
import {
  type AdvancedCommittee,
  createEventSignup,
  deleteEventById,
  deleteEventSignup,
  type Event,
  type EventSignUp,
  updateEventSignUp,
} from "@/lib"
import DeletionConfirmationDialog from "@/components/DeletionConfirmationDialog.vue"
import EventSignUpEdit from "@/components/events/EventSignUpEdit.vue"

const router = useRouter()
const props = defineProps({
  event: {
    type: Object as () => Event,
    required: true,
  },
  signUps: {
    type: Object as () => EventSignUp[],
    required: false,
    default: () => [] as EventSignUp[],
  },
  committees: {
    type: Object as () => AdvancedCommittee[],
    required: false,
    default: () => [] as AdvancedCommittee[],
  },
})

const showDeleteDialog = ref(false)
const deletingEvent = ref(false)
const emit = defineEmits<{
  (e: "deleted", id: number): void,
  (e: "updated", event: Event): void,
  (e: "signUp:updated", signUp: EventSignUp): void,
  (e: "signUp:deleted", signUpId: number): void,
}>()

async function confirmDeleteEvent() {
  if (!event.value?.id) return
  deletingEvent.value = true
  try {
    await deleteEventById({path: {eventId: event.value.id as number}})
    store.commit("setStatusSnackbarMessage", `Deleted “${event.value.title}”`)
    emit("deleted", event.value.id as number)
  } catch (err) {
    console.error(err)
    store.commit("setStatusSnackbarMessage", `Couldn't delete “${event.value.title}”`)
  } finally {
    deletingEvent.value = false
    showDeleteDialog.value = false
  }
}

const event = ref<Event>(props.event)
const signUp = computed(() => {
    const signupProp = props.signUps.find((s: EventSignUp) => s.eventId == event.value.id) ?? {}
    return {
      id: signupProp?.id,
      eventId: signupProp?.eventId,
      answers: signupProp?.answers ?? [],
    } as EventSignUp
  },
)

const route = useRoute()
const theme = useTheme()
const expanded = ref(false)
const submitting = ref(false)
const eventElement = ref<HTMLElement | null>(null)

const isMember = computed<boolean>(() => store.getters.isMember)
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const isBoard = computed<boolean>(() => store.getters.isBoard)

const committee = computed(() =>
  props.committees.some((c: AdvancedCommittee) => event.value.committeeId == c.id),
)

async function submitSignUp() {
  submitting.value = true
  try {
    var updatedSignUp: EventSignUp
    if (signUp.value?.id) {
      const resp = await updateEventSignUp({
        path: {eventId: event.value.id as number},
        body: signUp.value,
        throwOnError: true,
      })
      updatedSignUp = resp.data
    } else {
      const resp = await createEventSignup({
        path: {eventId: event.value.id as number},
        body: {
          ...signUp.value,
          eventId: event.value.id as number,
        },
        throwOnError: true,
      })
      updatedSignUp = resp.data
    }

    emit("signUp:updated", updatedSignUp)
  } finally {
    submitting.value = false
  }
}

async function removeSignUp() {
  if (signUp.value?.id !== undefined) {
    await deleteEventSignup({
      path: {eventSignupId: signUp.value.id as number},
      throwOnError: true,
    })
    emit("signUp:deleted", signUp.value.id)
  }
}

onMounted(async () => {
  if (route.hash && event.value.id === Number(route.hash.replace("#", ""))) {
    await nextTick()
    eventElement.value?.scrollIntoView({behavior: "smooth", block: "start"})
  }
})

function toggleExpanded() {
  if (!expanded.value) {
    eventElement.value?.scrollIntoView({behavior: "smooth", block: "start"})
  }
  expanded.value = !expanded.value
}

function findLocation() {
  if (event.value.location && event.value.location.toLowerCase().includes("discord")) {
    $goto("https://discord.gg/23YMFQy")
  } else if (event.value.location) {
    $goto(
      encodeURI("https://www.google.com/maps/search/?api=1&query=" + event.value.location),
    )
  }
}

function downloadIcs() {
  const start = DateTime.fromISO(event.value.startTime).toUTC()
  const end = DateTime.fromISO(event.value.endTime).toUTC()

  createIcsEvent(
    {
      title: event.value.title,
      description: event.value.description,
      location: event.value.location,
      start: [start.year, start.month, start.day, start.hour, start.minute],
      end: [end.year, end.month, end.day, end.hour, end.minute],
    },
    (error, value) => {
      if (error) {
        console.error(error)
        return
      }
      const element = document.createElement("a")
      element.setAttribute(
        "href",
        "data:text/plain;charset=utf-8," + encodeURIComponent(value || ""),
      )
      element.setAttribute("download", `${event.value.title}.ics`)
      element.style.display = "none"
      document.body.appendChild(element)
      element.click()
      document.body.removeChild(element)
    },
  )
}

async function copyShareLink() {
  const url = `${window.location.origin}${window.location.pathname}#${event.value.id}`
  await navigator.clipboard.writeText(url)
  store.commit("setStatusSnackbarMessage", `Link for ${event.value.title} copied to clipboard`)
}

function formatEventTime() {
  const startTime = DateTime.fromISO(event.value.startTime)
  const endTime = DateTime.fromISO(event.value.endTime)

  let result = ""

  result += startTime.toLocaleString({
    weekday: "long",
    day: "numeric",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
  })

  result += " - "

  if (
    startTime.day !== endTime.day ||
    startTime.month !== endTime.month ||
    startTime.year !== endTime.year
  ) {
    result += endTime.toLocaleString({
      weekday: "long",
      day: "numeric",
      month: "long",
    })
    result += " at "
  }

  result += endTime.toLocaleString({
    hour: "2-digit",
    minute: "2-digit",
  })

  return result
}

function handleUpdateSignUp(updatedSignUp: EventSignUp) {
  eventElement.value?.scrollIntoView({behavior: "smooth", block: "start"})
  emit("signUp:updated", updatedSignUp)
  expanded.value = false
}

</script>

<template v-if="event.id">
  <div>
    <v-list-item
      :style="{
        'background-image': !event.banner
          ? ''
          : theme.global.current.value.dark
            ? `linear-gradient(to bottom, rgba(0,0,0,0.7), rgba(0,0,0,0.7)), url(${event.banner})`
            : `linear-gradient(to bottom, rgba(255,255,255,0.9), rgba(255,255,255,0.9)), url(${event.banner})`
      }"
      class="py-4"
      rounded="sm"
      style="background-size: cover; background-position: center; min-height: 240px;"
    >
      <div ref="eventElement">
        <v-container>
          <v-row
            no-gutters
            class="align-stretch fill-height"
          >
            <!-- Left: fills remaining width -->
            <v-col class="flex-grow-1">
              <v-list-item-title class="text-h4">
                {{ event.title }}
              </v-list-item-title>

              <div
                class="text-subtitle-2 mb-2"
                style="opacity: var(--v-medium-emphasis-opacity);"
              >
                {{ event.location }} <br>
                {{ formatEventTime() }} <br>
                {{ event.membersOnly ? "Members only" : "" }}
              </div>

              <div v-html="event.description ? $markdownToHtml(event.description) : 'No description...'" />
            </v-col>

            <!-- Right: only as wide as its content; full height; centered content -->
            <v-col
              cols="auto"
              class="align-self-stretch d-flex"
            >
              <div class="d-flex flex-column align-center justify-center h-100">
                <template v-if="committee">
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
                        @click="router.push(`/events/signups/${event.id}`)"
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
                        @click="router.push(`/events/edit/${event.id}`)"
                      />
                    </template>
                  </v-tooltip>

                  <template v-if="isBoard">
                    <v-tooltip
                      location="left"
                      text="Delete event"
                    >
                      <template #activator="{ props: tooltip }">
                        <v-btn
                          icon="mdi-delete"
                          v-bind="tooltip"
                          variant="plain"
                          @click="showDeleteDialog = true"
                        />
                      </template>
                    </v-tooltip>
                  </template>
                </template>

                <!-- Sign-up controls -->
                <template v-if="event.signUp">
                  <template v-if="isLoggedIn && !event.signUpForm">
                    <v-tooltip
                      v-if="signUp?.id"
                      location="left"
                      text="Cancel sign-up"
                    >
                      <template #activator="{ props: tooltipProps }">
                        <v-btn
                          :disabled="event.membersOnly && !isMember"
                          :loading="submitting"
                          icon="mdi-checkbox-marked"
                          v-bind="tooltipProps"
                          variant="plain"
                          @click="removeSignUp()"
                        />
                      </template>
                    </v-tooltip>

                    <v-tooltip
                      v-else
                      location="left"
                      text="Sign Up"
                    >
                      <template #activator="{ props: tooltipProps }">
                        <v-btn
                          :disabled="event.membersOnly && !isMember"
                          :loading="submitting"
                          icon="mdi-checkbox-blank"
                          v-bind="tooltipProps"
                          variant="plain"
                          @click="submitSignUp()"
                        />
                      </template>
                    </v-tooltip>
                  </template>

                  <template v-else-if="event.signUpForm && (isLoggedIn || !event.membersOnly)">
                    <v-tooltip
                      v-if="isLoggedIn && signUp?.id !== undefined"
                      location="left"
                      text="Cancel sign-up"
                    >
                      <template #activator="{ props: tooltipProps }">
                        <v-btn
                          :disabled="event.membersOnly && !isMember"
                          :loading="submitting"
                          icon="mdi-close"
                          v-bind="tooltipProps"
                          variant="plain"
                          @click="removeSignUp()"
                        />
                      </template>
                    </v-tooltip>

                    <v-tooltip
                      :text="
                        signUp?.id
                          ? 'Edit sign-up form'
                          : expanded
                            ? 'Cancel filling in sign-up form'
                            : 'Fill in sign-up form'
                      "
                      location="left"
                    >
                      <template #activator="{ props: tooltipProps }">
                        <v-btn
                          :disabled="event.membersOnly && !isMember"
                          :loading="submitting"
                          icon="mdi-list-status"
                          v-bind="tooltipProps"
                          variant="plain"
                          @click="toggleExpanded()"
                        />
                      </template>
                    </v-tooltip>
                  </template>
                </template>

                <!-- Utility actions -->
                <v-tooltip
                  location="left"
                  text="Find location"
                >
                  <template #activator="{ props: tooltipProps }">
                    <v-btn
                      icon="mdi-google-maps"
                      v-bind="tooltipProps"
                      variant="plain"
                      @click="findLocation()"
                    />
                  </template>
                </v-tooltip>

                <v-tooltip
                  location="left"
                  text="Add to your calendar"
                >
                  <template #activator="{ props: tooltipProps }">
                    <v-btn
                      icon="mdi-calendar"
                      v-bind="tooltipProps"
                      variant="plain"
                      @click="downloadIcs()"
                    />
                  </template>
                </v-tooltip>

                <v-tooltip
                  location="left"
                  text="Copy share link"
                >
                  <template #activator="{ props: tooltipProps }">
                    <v-btn
                      icon="mdi-share-variant"
                      v-bind="tooltipProps"
                      variant="plain"
                      @click="copyShareLink()"
                    />
                  </template>
                </v-tooltip>
              </div>
            </v-col>
          </v-row>

          <v-row>
            <v-expand-transition :key="event.id">
              <div
                v-if="expanded"
                class="form-border mx-auto rounded-b w-100"
              >
                <event-sign-up-edit
                  :event="event"
                  :initial-form-answers="signUp?.answers"
                  :initial-sign-up="signUp"
                  :show-guest-form="!isLoggedIn"
                  class="form mx-auto"
                  @update:sign-up="handleUpdateSignUp"
                />
              </div>
            </v-expand-transition>
          </v-row>
        </v-container>
      </div>
    </v-list-item>


    <deletion-confirmation-dialog
      v-model="showDeleteDialog"
      :title="`Delete event`"
      :message="`Are you sure you want to delete “${event.title}”? This can’t be undone.`"
      @confirm="confirmDeleteEvent"
    />
  </div>
</template>

<style lang="scss" scoped>
.form-border {
  border-width: 1px;
  border-color: rgb(var(--v-theme-accent));
  border-style: solid;
  border-radius: 5px;
}

.form {
  padding: 16px;
}
</style>
