<script lang="ts" setup>
import $markdownToHtml from "@/plugins/markdownToHtml.ts"
import {computed, nextTick, onBeforeUnmount, onMounted, type PropType, ref, toRef} from "vue"
import {createEvent as createIcsEvent} from "ics"
import store from "@/plugins/store.ts"
import {$goto} from "@/plugins/goto"
import {useRoute, useRouter} from "vue-router"
import {useTheme} from "vuetify"
import {DateTime} from "luxon"
import {
  type AdvancedCommittee,
  approveEvent,
  createEventSignup,
  deleteEventById,
  deleteEventSignup,
  downloadEventBanner,
  type Event,
  type EventSignUp,
  updateEventSignUp,
} from "@/lib"
import DeletionConfirmationDialog from "@/components/DeletionConfirmationDialog.vue"
import EventSignUpEdit from "@/components/events/EventSignUpEdit.vue"

const router = useRouter()
const theme = useTheme()
const route = useRoute()

const props = defineProps({
  event: {
    type: Object as PropType<Event>,
    required: true,
  },
  signUps: {
    type: Array as PropType<EventSignUp[]>,
    default: () => [],
  },
  committees: {
    type: Array as PropType<AdvancedCommittee[]>,
    default: () => [],
  },
})

const emit = defineEmits<{
  (e: "delete:event", id: number): void
  (e: "update:event", event: Event): void
  (e: "update:signUp", signUp: EventSignUp): void
  (e: "delete:signUp", signUpId: number): void
}>()

const event = toRef(props, "event")

const signUp = computed<EventSignUp | undefined>(() =>
  props.signUps.find((s) => s.eventId === event.value.id),
)

const expanded = ref(false)
const submitting = ref(false)
const showDeleteDialog = ref(false)
const deletingEvent = ref(false)
const eventElement = ref<HTMLElement | null>(null)

const isMember = computed<boolean>(() => store.getters.isMember)
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const isBoard = computed<boolean>(() => store.getters.isBoard)

const committee = computed<AdvancedCommittee | undefined>(() =>
  props.committees.find((c) => event.value.committeeId === c.id),
)

async function confirmDeleteEvent() {
  if (!event.value?.id) return
  deletingEvent.value = true
  try {
    await deleteEventById({path: {eventId: event.value.id as number}})
    store.commit("setStatusSnackbarMessage", `Deleted “${event.value.title}”`)
    emit("delete:event", event.value.id as number)
  } catch (err) {
    console.error(err)
    store.commit("setStatusSnackbarMessage", `Couldn't delete “${event.value.title}”`)
  } finally {
    deletingEvent.value = false
    showDeleteDialog.value = false
  }
}

async function submitSignUp() {
  if (!event.value?.id) return
  submitting.value = true
  try {
    let updatedSignUp: EventSignUp
    if (signUp.value?.id) {
      const resp = await updateEventSignUp({
        path: {eventId: event.value.id as number},
        body: {...signUp.value},
        throwOnError: true,
      })
      updatedSignUp = resp.data
    } else {
      const resp = await createEventSignup({
        path: {eventId: event.value.id as number},
        body: {eventId: event.value.id as number, answers: []},
        throwOnError: true,
      })
      updatedSignUp = resp.data
    }
    emit("update:signUp", updatedSignUp)
  } finally {
    submitting.value = false
  }
}

async function removeSignUp() {
  if (signUp.value?.id === undefined) return
  await deleteEventSignup({path: {eventSignupId: signUp.value.id as number}, throwOnError: true})
  emit("delete:signUp", signUp.value.id)
}

async function toggleEventApproved() {
  const resp = await approveEvent({
    path: {id: event.value.id!},
    query: {approved: !event.value.approved},
    throwOnError: true,
  })
  emit("update:event", resp.data)
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
    $goto(encodeURI("https://www.google.com/maps/search/?api=1&query=" + event.value.location))
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
      if (error) return console.error(error)
      const element = document.createElement("a")
      element.setAttribute("href", "data:text/plain;charset=utf-8," + encodeURIComponent(value || ""))
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
  if (startTime.hasSame(endTime, "day") && startTime.hasSame(endTime, "month") && startTime.hasSame(endTime, "year")) {
    // same day
  } else {
    result += endTime.toLocaleString({weekday: "long", day: "numeric", month: "long"})
    result += " at "
  }
  result += endTime.toLocaleString({hour: "2-digit", minute: "2-digit"})
  return result
}

const isApproved = computed<boolean>(() => !!event.value?.approved)
const approvedLabel = computed(() => (isApproved.value ? "Approved" : "Awaiting approval"))
const approvedIcon = computed(() => (isApproved.value ? "mdi-check-circle" : "mdi-close-circle"))
const approvedColor = computed(() => (isApproved.value ? "success" : "warning"))

const bannerUrl = ref<string | null>(null)

async function loadBanner() {
  if (!event.value?.id || !event.value.banner) return
  try {
    const resp = await downloadEventBanner({
      path: {
        bannerId: event.value.banner.id!,
      },
      throwOnError: true,
      responseType: "blob",
    })

    const blob = resp?.data as Blob
    if (bannerUrl.value) URL.revokeObjectURL(bannerUrl.value)
    bannerUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    console.error("Failed to download event banner:", e)
  }
}

onMounted(loadBanner)

onBeforeUnmount(() => {
  if (bannerUrl.value) URL.revokeObjectURL(bannerUrl.value)
})

function handleUpdateSignUp(updatedSignUp: EventSignUp) {
  eventElement.value?.scrollIntoView({behavior: "smooth", block: "start"})
  emit("update:signUp", updatedSignUp)
  expanded.value = false
}
</script>

<template v-if="event.id">
  <div>
    <v-list-item
      :style="bannerUrl
        ? {
          backgroundImage: theme.global.current.value.dark
            ? `linear-gradient(to bottom, rgba(0,0,0,0.7), rgba(0,0,0,0.7)), url('${bannerUrl}')`
            : `linear-gradient(to bottom, rgba(255,255,255,0.7), rgba(255,255,255,0.7)), url('${bannerUrl}')`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
          backgroundRepeat: 'no-repeat',
          minHeight: '240px'
        }
        : {
          minHeight: '240px'
        }"
      class="py-4"
      rounded="sm"
      style="background-size: cover; background-position: center; min-height: 240px;"
    >
      <div ref="eventElement">
        <v-container>
          <v-row
            no-gutters
            class="align-stretch fill-height flex-nowrap"
          >
            <v-col class="flex-grow-1 min-w-0">
              <v-list-item-title class="text-h4 d-flex align-center ga-2">
                <span
                  class="text-wrap"
                  style="word-break: break-word;"
                >
                  {{ event.title }}
                  <v-tooltip
                    v-if="isBoard || committee"
                    location="bottom"
                    :text="isApproved ? 'Mark as awaiting approval' : 'Mark as approved'"
                  >
                    <template #activator="{ props: approveProps }">
                      <v-btn
                        class="approve-btn text-none"
                        v-bind="approveProps"
                        :prepend-icon="approvedIcon"
                        :disabled="!isBoard || DateTime.fromISO(event.startTime) < DateTime.now()"
                        :color="approvedColor"
                        variant="tonal"
                        size="small"
                        @click="toggleEventApproved"
                      >
                        {{ approvedLabel }}
                      </v-btn>
                    </template>
                  </v-tooltip>
                </span>
              </v-list-item-title>

              <div
                class="text-subtitle-2 mb-2 medium-emphasis-opacity"
              >
                {{ event.location }} <br>
                {{ formatEventTime() }} <br>
                {{ event.membersOnly ? "Members only" : "" }}
              </div>

              <div
                class="text-wrap"
                style="word-break: break-word"
                v-html="event.description ? $markdownToHtml(event.description) : 'No description...'"
              />
            </v-col>

            <v-col
              cols="auto"
              class="align-self-stretch d-flex shrink-0"
            >
              <div class="right-rail d-flex flex-column align-end justify-start h-100">
                <div
                  v-if="committee"
                  class="mb-3"
                >
                  <v-sheet
                    class="top-right-header"
                    border
                  >
                    <span class="committee-name text-caption font-weight-medium font-weight-bold">
                      {{ committee.name }}
                    </span>

                    <div class="top-right-actions">
                      <v-tooltip
                        location="bottom"
                        text="Check signups"
                      >
                        <template #activator="{ props: checkSignUpsProp }">
                          <v-btn
                            :disabled="!event.signUp"
                            icon="mdi-list-status"
                            v-bind="checkSignUpsProp"
                            variant="plain"
                            @click="router.push(`/events/signups/${event.id}`)"
                          />
                        </template>
                      </v-tooltip>

                      <v-tooltip
                        location="bottom"
                        text="Edit event"
                      >
                        <template #activator="{ props: editEventProps }">
                          <v-btn
                            icon="mdi-pencil"
                            v-bind="editEventProps"
                            variant="plain"
                            @click="router.push(`/events/edit/${event.id}`)"
                          />
                        </template>
                      </v-tooltip>

                      <v-tooltip
                        location="bottom"
                        text="Delete event"
                      >
                        <template #activator="{ props: deleteProps }">
                          <v-btn
                            icon="mdi-delete"
                            v-bind="deleteProps"
                            variant="plain"
                            @click="showDeleteDialog = true"
                          />
                        </template>
                      </v-tooltip>
                    </div>
                  </v-sheet>
                </div>

                <template v-if="event.signUp">
                  <template v-if="!event.signUpForm">
                    <v-tooltip
                      v-if="signUp?.id"
                      location="left"
                      text="Cancel sign-up"
                    >
                      <template #activator="{ props: tooltipProps }">
                        <v-badge
                          v-if="event.signUpCount > 0"
                          color="blue"
                          :content="event.signUpCount"
                          offset-x="8"
                          offset-y="8"
                        />
                        <v-btn
                          :disabled="(event.membersOnly && !isMember) || DateTime.fromISO(event.startTime) < DateTime.now()"
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
                        <v-badge
                          color="blue"
                          :content="event.signUpCount"
                          offset-x="8"
                          offset-y="8"
                        />
                        <v-btn
                          :disabled="(event.membersOnly && !isMember) || DateTime.fromISO(event.startTime) < DateTime.now()"
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
                          :disabled="(event.membersOnly && !isMember) || DateTime.fromISO(event.startTime) < DateTime.now()"
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
                          ? (expanded ? 'Cancel editing sign-up form' : 'Edit sign-up form')
                          : (expanded ? 'Cancel filling in sign-up form' : 'Fill in sign-up form')
                      "
                      location="left"
                    >
                      <template #activator="{ props: tooltipProps }">
                        <v-badge
                          color="blue"
                          :content="event.signUpCount"
                          offset-x="8"
                          offset-y="8"
                        />
                        <v-btn
                          :disabled="(event.membersOnly && !isMember) || DateTime.fromISO(event.startTime) < DateTime.now()"
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
.flex-nowrap {
  flex-wrap: nowrap !important;
}

.approve-btn {
  flex: 0 0 auto;
  white-space: nowrap;
}

.right-rail {
  inline-size: max-content;
}

.form-border {
  border-width: 1px;
  border-color: rgb(var(--v-theme-accent));
  border-style: solid;
  border-radius: 5px;
}

.form {
  padding: 16px;
}


.top-right-header {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 4px;
  border-width: 1px;
  border-style: solid;
  border-radius: 6px;
  padding: 3px 4px;
}

.top-right-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.committee-name {
  opacity: var(--v-medium-emphasis-opacity);
  padding-inline-start: 10px;
  padding-left: 10px;
}

.middle-actions {
  align-self: stretch;
}

.medium-emphasis-opacity {
  opacity: var(--v-medium-emphasis-opacity)
}


</style>
