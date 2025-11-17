<script lang="ts" setup>
import $markdownToHtml from "@/plugins/markdownToHtml.ts"
import {computed, nextTick, onBeforeUnmount, onMounted, type PropType, ref, toRef} from "vue"
import {createEvent as createIcsEvent} from "ics"
import store from "@/plugins/store.ts"
import {$goto} from "@/plugins/goto.ts"
import {useRoute, useRouter} from "vue-router"
import {useTheme} from "vuetify"
import {DateTime} from "luxon"
import {
  type AdvancedCommittee,
  approveEvent,
  deleteEventById,
  deleteEventSignup,
  downloadEventBanner,
  type Event,
  type EventSignUp,
} from "@/services/api"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"

const router = useRouter()
const theme = useTheme()
const route = useRoute()

const props = defineProps({
  event: {type: Object as PropType<Event>, required: true},
  signUps: {type: Array as PropType<EventSignUp[]>, default: () => []},
  committees: {type: Array as PropType<AdvancedCommittee[]>, default: () => []},
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

const isPastEvent = computed(() => DateTime.fromISO(event.value.startTime) < DateTime.now())
const actionsDisabled = computed(
  () => !event.value.approved || (event.value.membersOnly && !isMember.value) || isPastEvent.value,
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

async function removeSignUp() {
  if (signUp.value?.id === undefined) return
  await deleteEventSignup({
    path: {eventSignupId: signUp.value.id as number},
    query: {accessToken: signUp.value.guest?.accessToken},
    throwOnError: true,
  })
  expanded.value = false
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
      element.remove()
    },
  )
}

async function copyShareLink() {
  const url = `${globalThis.location.origin}${globalThis.location.pathname}#${event.value.id}`
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
  if (!(startTime.hasSame(endTime, "day") && startTime.hasSame(endTime, "month") && startTime.hasSame(endTime, "year"))) {
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
      path: {bannerId: event.value.banner.id!},
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

function updateSignUp(updatedSignUp: EventSignUp) {
  eventElement.value?.scrollIntoView({behavior: "smooth", block: "start"})
  emit("update:signUp", updatedSignUp)
  expanded.value = false
}

const cardStyle = computed(() => {
  const base = {
    minHeight: "240px",
    backgroundSize: "cover",
    backgroundPosition: "center",
    backgroundRepeat: "no-repeat",
  } as Record<string, string>
  if (bannerUrl.value) {
    const overlay = theme.global.current.value.dark ? "rgba(0,0,0,0.7)" : "rgba(255,255,255,0.7)"
    return {
      ...base,
      backgroundImage: `linear-gradient(to bottom, ${overlay}, ${overlay}), url('${bannerUrl.value}')`,
    }
  }
  return base
})

const isSignedUp = computed<boolean>(() => signUp.value?.id !== undefined)

const signUpIcon = computed(() =>
  isSignedUp.value ? "mdi-account-multiple" : "mdi-account-multiple-plus",
)

const signUpStatusIcon = computed(() =>
  isSignedUp.value ? "mdi-checkbox-marked-circle" : "mdi-close-circle",
)

const signUpStatusColor = computed(() =>
  isSignedUp.value ? "success" : "orange",
)
</script>

<template v-if="event.id">
  <div>
    <v-card
      class="card--row"
      :style="cardStyle"
      rounded="sm"
    >
      <v-container class="pa-1">
        <div class="content--row">
          <div
            ref="eventElement"
            class="main"
          >
            <v-card-item>
              <v-card-title class="text-h4 d-flex align-center ga-2">
                <span
                  class="text-wrap"
                  style="word-break: break-word;"
                >
                  {{ event.title }}
                  <v-tooltip
                    v-if="isBoard || committee"
                    :text="isApproved ? 'Mark as awaiting approval' : 'Mark as approved'"
                    location="bottom"
                  >
                    <template #activator="{ props: approveProps }">
                      <v-btn
                        :color="approvedColor"
                        :disabled="!isBoard || isPastEvent"
                        :prepend-icon="approvedIcon"
                        class="approve-btn text-none"
                        size="small"
                        v-bind="approveProps"
                        variant="tonal"
                        @click="toggleEventApproved"
                      >
                        {{ approvedLabel }}
                      </v-btn>
                    </template>
                  </v-tooltip>
                </span>
              </v-card-title>

              <v-card-subtitle>
                {{ event.location }} <br>
                {{ formatEventTime() }} <br>
                <span
                  v-if="event.membersOnly"
                  :class="['v-card-subtitle', {
                    'text-red': !isMember,
                    'font-weight-bold': !isMember,
                    'text-decoration-underline': !isMember,
                  }]"
                >
                  Members only
                </span>
              </v-card-subtitle>
            </v-card-item>

            <v-card-text>
              <div
                class="text-wrap"
                style="word-break: break-word"
                v-html="event.description ? $markdownToHtml(event.description) : 'No description...'"
              />
            </v-card-text>
          </div>

          <v-card-actions class="actions-rail d-flex flex-column align-end">
            <div
              v-if="committee"
              class="mb-3 w-100"
            >
              <v-sheet
                border
                class="top-right-header"
              >
                <span class="committee-name text-caption font-weight-medium">
                  {{ committee.name }}
                </span>
                <div class="top-right-actions">
                  <v-tooltip
                    location="left"
                    text="Check signups"
                  >
                    <template #activator="{ props: p }">
                      <v-btn
                        :disabled="!event.signUp"
                        icon="mdi-list-status"
                        v-bind="p"
                        variant="plain"
                        @click="router.push(`/events/signups/${event.id}`)"
                      />
                    </template>
                  </v-tooltip>

                  <v-tooltip
                    location="left"
                    text="Edit event"
                  >
                    <template #activator="{ props: p }">
                      <v-btn
                        icon="mdi-pencil"
                        v-bind="p"
                        variant="plain"
                        @click="router.push(`/events/edit/${event.id}`)"
                      />
                    </template>
                  </v-tooltip>

                  <v-tooltip
                    location="left"
                    text="Delete event"
                  >
                    <template #activator="{ props: p }">
                      <v-btn
                        icon="mdi-delete"
                        v-bind="p"
                        variant="plain"
                        @click="showDeleteDialog = true"
                      />
                    </template>
                  </v-tooltip>
                </div>
              </v-sheet>
            </div>


            <v-tooltip
              location="left"
              text="Find location"
            >
              <template #activator="{ props: p }">
                <v-btn
                  icon="mdi-google-maps"
                  v-bind="p"
                  variant="plain"
                  @click="findLocation()"
                />
              </template>
            </v-tooltip>

            <v-tooltip
              location="left"
              text="Add to your calendar"
            >
              <template #activator="{ props: p }">
                <v-btn
                  icon="mdi-calendar"
                  v-bind="p"
                  variant="plain"
                  @click="downloadIcs()"
                />
              </template>
            </v-tooltip>

            <v-tooltip
              location="left"
              text="Copy share link"
            >
              <template #activator="{ props: p }">
                <v-btn
                  icon="mdi-share-variant"
                  v-bind="p"
                  variant="plain"
                  @click="copyShareLink()"
                />
              </template>
            </v-tooltip>

            <template v-if="event.signUp">
              <v-tooltip
                v-if="signUp?.id !== undefined"
                location="left"
                text="Cancel sign-up"
              >
                <template #activator="{ props: p }">
                  <v-btn
                    :disabled="actionsDisabled"
                    :loading="submitting"
                    icon="mdi-account-multiple-remove"
                    v-bind="p"
                    variant="plain"
                    @click="removeSignUp()"
                  />
                </template>
              </v-tooltip>

              <v-tooltip
                :text="
                  signUp?.id
                    ? (expanded ? 'Cancel editing sign-up' : 'Edit sign-up')
                    : (expanded ? 'Cancel signing up' : 'Sign up')
                "
                location="left"
              >
                <template #activator="{ props: p }">
                  <span
                    class="action-btn-wrap"
                    v-bind="p"
                  >
                    <v-badge
                      color="primary"
                      :content="event.signUpCount"
                      floating
                      offset-x="15"
                      offset-y="15"
                    >
                      <v-badge
                        floating
                        offset-x="21"
                        offset-y="40"
                        color="transparent"
                      >
                        <template #badge>
                          <v-avatar
                            :size="18"
                            color="transparent"
                            class="pa-0"
                          >
                            <v-icon
                              :icon="signUpStatusIcon"
                              :size="18"
                              :color="signUpStatusColor"
                            />
                          </v-avatar>
                        </template>

                        <v-btn
                          :disabled="actionsDisabled"
                          :loading="submitting"
                          :icon="signUpIcon"
                          variant="plain"
                          :aria-label="
                            isSignedUp
                              ? (expanded ? 'Cancel editing sign-up' : 'Edit sign-up')
                              : (expanded ? 'Cancel signing up' : 'Sign up')
                          "
                          @click="toggleExpanded()"
                        />
                      </v-badge>
                    </v-badge>
                  </span>
                </template>
              </v-tooltip>
            </template>
          </v-card-actions>
        </div>

        <v-expand-transition :key="event.id">
          <div
            v-if="expanded"
            class="mx-auto w-100 pa-2"
          >
            <event-sign-up-form
              :event="event"
              :initial-sign-up="signUp"
              :show-guest-form="!isLoggedIn"
              class="sign-up-form mx-auto"
              @update:sign-up="updateSignUp"
            />
          </div>
        </v-expand-transition>
      </v-container>
    </v-card>

    <deletion-confirmation-dialog
      v-model="showDeleteDialog"
      :message="`Are you sure you want to delete “${event.title}”? This can’t be undone.`"
      :title="`Delete event`"
      @confirm="confirmDeleteEvent"
    />
  </div>
</template>

<style lang="scss" scoped>
.card--row {
  display: flex;
  flex-direction: column;
}

.content--row {
  display: flex;
  flex-direction: row;
  align-items: stretch;
}

.main {
  flex: 1 1 auto;
  min-width: 0;
}

.actions-rail {
  width: fit-content;
  padding: 8px 4px;
  gap: 6px;
}

.approve-btn {
  flex: 0 0 auto;
  white-space: nowrap;
}

.sign-up-form {
  border-radius: 10px;
  border-width: 1px;
  border-color: rgba(var(--v-theme-accent), 0.6);
  border-style: solid;
  padding: 16px;
  background-color: rgba(var(--v-theme-surface), 0.5);
  backdrop-filter: blur(8px) saturate(120%);
  -webkit-backdrop-filter: blur(8px) saturate(120%);
}

.top-right-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.top-right-header {
  --xpad: 0px;
  display: grid;
  grid-template-columns: auto auto;
  align-items: stretch;
  gap: 2px;

  border-width: 1px;
  border-style: solid;
  border-radius: 8px;
  padding: 6px;
  margin-right: 2px !important;

  background-color: rgba(var(--v-theme-surface), 0.5);
  backdrop-filter: blur(8px) saturate(120%);
  -webkit-backdrop-filter: blur(8px) saturate(120%);
  border-color: rgba(var(--v-theme-accent), 0.6);
}

.top-right-header :deep(.v-btn),
.top-right-header .committee-name {
  padding: var(--xpad) !important;
}


.committee-name {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  opacity: var(--v-medium-emphasis-opacity);
}
</style>
