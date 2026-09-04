<script lang="ts" setup>
import $markdownToHtml from "@/plugins/markdownToHtml.ts"
import {computed, nextTick, onMounted, type PropType, ref, toRef} from "vue"
import {createEvent as createIcsEvent} from "ics"
import store from "@/plugins/store.ts"
import {$goto} from "@/plugins/goto.ts"
import {useRoute, useRouter} from "vue-router"
import {useTheme} from "vuetify"
import {DateTime} from "luxon"
import {
  apiUrl,
  approveEvent,
  type CommitteeDetailResponse,
  deleteEventById,
  deleteEventSignup,
  type EventResponse,
  type EventSignUpResponse,
} from "@/services/api"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"
import sadgeImg from "@/assets/icons/sadge-icon.png"
import type {GuestSessionData} from "@/plugins/store.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const router = useRouter()
const theme = useTheme()
const route = useRoute()

type Event = EventResponse
type EventSignUp = EventSignUpResponse
type CommitteeOption = Pick<CommitteeDetailResponse, "id" | "name">

const props = defineProps({
  event: {type: Object as PropType<Event>, required: true},
  signUps: {type: Array as PropType<EventSignUp[]>, default: () => []},
  committees: {type: Array as PropType<CommitteeOption[]>, default: () => []},
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

const committee = computed<CommitteeOption | undefined>(() =>
  props.committees.find((c) => event.value.committeeId === c.id),
)

const isPastEvent = computed(() => DateTime.fromISO(event.value.startTime) < DateTime.now())
const actionsDisabled = computed(
  () => !event.value.approved || (event.value.membersOnly && !isMember.value) || isPastEvent.value,
)

const deadlinePassed = computed<boolean>(() => {
  if (!event.value.signUpDeadline) return false
  return DateTime.fromISO(event.value.signUpDeadline) < DateTime.now()
})

const atCapacity = computed<boolean>(() => {
  const limit = event.value.signUpLimit
  if (limit == null) return false
  return (event.value.signUpCount ?? 0) >= limit
})

const signUpBlockedReason = computed<string | null>(() => {
  if (isSignedUp.value) return null
  if (deadlinePassed.value) return "Sign-up deadline has passed"
  if (atCapacity.value) return "This event is full"
  return null
})

const signUpDisabled = computed<boolean>(
  () => actionsDisabled.value || signUpBlockedReason.value !== null,
)

const signUpTooltip = computed<string>(() => {
  if (signUpBlockedReason.value) return signUpBlockedReason.value
  if (signUp.value?.id) {
    if (!hasSignUpForm.value) return "Sign me out"
    return expanded.value ? "Cancel editing sign-up" : "Edit sign-up"
  }
  return expanded.value ? "Cancel signing up" : "Sign up"
})

const signUpCountLabel = computed<string>(() => {
  const count = event.value.signUpCount ?? 0
  const limit = event.value.signUpLimit
  return limit != null ? `${count}/${limit}` : `${count}`
})

const signUpDeadlineLabel = computed<string | null>(() => {
  if (!event.value.signUpDeadline) return null
  return DateTime.fromISO(event.value.signUpDeadline).toLocaleString({
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  })
})

async function confirmDeleteEvent() {
  if (!event.value?.id) return
  deletingEvent.value = true
  try {
    await deleteEventById({path: {eventId: event.value.id as number}, throwOnError: true})
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
      description: event.value.description ?? undefined,
      location: event.value.location ?? undefined,
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

/** The art the event answers with, or nothing where it carries none. */
const banner = computed(() => event.value?.banner?.image ?? null)

/** About how wide this card's background is drawn, which decides the copy worth fetching. */
const BANNER_CSS_WIDTH = 640

/**
 * The art as a background, at a width close to what the card actually draws.
 *
 * `image-set` takes resolutions, not widths: a background has no layout to measure the way
 * `srcset` does, so the choice is made here instead — the copy nearest the drawn width for a
 * normal screen, and the one nearest twice that for a dense one. Anything stored at a single
 * width has nothing to pick between and stays a plain `url`.
 */
const bannerBackground = computed<string | null>(() => {
  const art = banner.value
  if (!art?.url) return null
  const full = apiUrl(art.url)
  const widths = (art.renditions ?? [])
    .filter((copy): copy is {url: string; width: number} => !!copy.url && !!copy.width)
  if (widths.length === 0) return `url('${full}')`

  const nearest = (wanted: number) => widths
    .reduce((best, copy) => (
      Math.abs(copy.width - wanted) < Math.abs(best.width - wanted) ? copy : best
    ))
  const single = nearest(BANNER_CSS_WIDTH)
  const dense = nearest(BANNER_CSS_WIDTH * 2)
  if (dense.url === single.url) return `url('${apiUrl(single.url)}')`
  return `image-set(url('${apiUrl(single.url)}') 1x, url('${apiUrl(dense.url)}') 2x)`
})

function updateSignUp(updatedSignUp: EventSignUp) {
  eventElement.value?.scrollIntoView({behavior: "smooth", block: "start"})
  emit("update:signUp", updatedSignUp)
  expanded.value = false
}

function deleteSignUp(signUpId: number) {
  expanded.value = false
  emit("delete:signUp", signUpId)
}

const cardStyle = computed(() => {
  const base = {
    minHeight: "240px",
    backgroundSize: "cover",
    backgroundPosition: "center",
    backgroundRepeat: "no-repeat",
  } as Record<string, string>
  const art = bannerBackground.value
  if (art) {
    const overlay = theme.global.current.value.dark ? "rgba(0,0,0,0.7)" : "rgba(255,255,255,0.7)"
    return {
      ...base,
      backgroundImage: `linear-gradient(to bottom, ${overlay}, ${overlay}), ${art}`,
    }
  }
  return base
})

const isSignedUp = computed<boolean>(() => signUp.value?.id !== undefined)
const hasSignUpForm = computed<boolean>(
  () => (event.value.signUpForm?.questions?.length ?? 0) > 0,
)
const signOutInline = computed<boolean>(() => isSignedUp.value && !hasSignUpForm.value)
const signUpHover = ref(false)
const signingOut = ref(false)

const signUpIcon = computed(() => {
  if (!isSignedUp.value) return "mdi-account-multiple-plus"
  if (signOutInline.value) return signUpHover.value ? null : "mdi-account-check"
  return signUpHover.value && !expanded.value ? "mdi-pencil" : "mdi-account-check"
})

const showSadgeIcon = computed(() => signOutInline.value && signUpHover.value)
const signUpButtonColor = computed(() => {
  if (showSadgeIcon.value) return "error"
  return isSignedUp.value ? "success" : undefined
})
const signUpButtonVariant = computed(() => (isSignedUp.value ? "tonal" : "plain"))

async function directSignOut() {
  const existing = signUp.value
  if (!existing?.id) return
  signingOut.value = true
  try {
    const guestAccessToken =
      (store.getters.getGuestData as GuestSessionData | null)?.accessToken ?? null
    await deleteEventSignup({
      path: {id: existing.id as number},
      headers: guestAccessToken ? {"X-Guest-Access-Token": guestAccessToken} : undefined,
      throwOnError: true,
    })
    emit("delete:signUp", existing.id as number)
  } catch (err) {
    $handleNetworkError(err)
  } finally {
    signingOut.value = false
  }
}

function handleSignUpClick() {
  if (signOutInline.value) {
    directSignOut()
  } else {
    toggleExpanded()
  }
}
</script>

<template v-if="event.id">
  <div>
    <v-card
      :data-testid="`event-card-${event.id}`"
      :style="cardStyle"
      class="card--row"
      rounded="sm"
    >
      <v-container class="py-1 px-2">
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
                        :data-testid="`event-approve-btn-${event.id}`"
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
                <span class="event-meta">{{ event.location }}</span><br>
                <span class="event-meta">{{ formatEventTime() }}</span><br>
                <span
                  v-if="event.signUp && signUpDeadlineLabel"
                  class="event-meta"
                  :class="{ 'text-error': deadlinePassed }"
                >
                  {{ deadlinePassed ? 'Sign-ups closed:' : 'Sign-ups close:' }} {{ signUpDeadlineLabel }}
                </span>
                <br v-if="event.signUp && signUpDeadlineLabel && event.membersOnly">
                <span
                  v-if="event.membersOnly"
                  class="event-meta"
                  :class="{ 'text-error': !isMember }"
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
                        :data-testid="`event-signups-btn-${event.id}`"
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
                        :data-testid="`event-edit-btn-${event.id}`"
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
                        :data-testid="`event-delete-btn-${event.id}`"
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

            <v-tooltip
              v-if="event.signUp"
              :text="signUpTooltip"
              location="left"
            >
              <template #activator="{ props: p }">
                <span
                  class="action-btn-wrap"
                  v-bind="p"
                >
                  <v-btn
                    v-if="showSadgeIcon"
                    :aria-label="signUpTooltip"
                    :data-testid="`event-signup-toggle-btn-${event.id}`"
                    :disabled="signUpDisabled || signingOut"
                    icon
                    :loading="submitting || signingOut"
                    :color="signUpButtonColor"
                    :variant="signUpButtonVariant"
                    @click="handleSignUpClick"
                    @mouseenter="signUpHover = true"
                    @mouseleave="signUpHover = false"
                    @focus="signUpHover = true"
                    @blur="signUpHover = false"
                  >
                    <img
                      :src="sadgeImg"
                      alt=""
                      class="signup-sadge"
                    >
                  </v-btn>
                  <v-btn
                    v-else
                    :aria-label="signUpTooltip"
                    :data-testid="`event-signup-toggle-btn-${event.id}`"
                    :disabled="signUpDisabled || signingOut"
                    :icon="signUpIcon"
                    :loading="submitting || signingOut"
                    :color="signUpButtonColor"
                    :variant="signUpButtonVariant"
                    @click="handleSignUpClick"
                    @mouseenter="signUpHover = true"
                    @mouseleave="signUpHover = false"
                    @focus="signUpHover = true"
                    @blur="signUpHover = false"
                  />
                  <v-chip
                    :color="atCapacity ? 'error' : 'primary'"
                    class="signup-count-label"
                    size="small"
                    variant="flat"
                  >{{ signUpCountLabel }}</v-chip>
                </span>
              </template>
            </v-tooltip>
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
              @delete:sign-up="deleteSignUp"
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
  // The card title is text-h4; without an explicit size the marker inherits
  // that large font under Vuetify 4. Keep it a small badge.
  font-size: 0.7rem !important;
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

.action-btn-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.signup-count-label {
  margin-top: -12px;
  font-family: Roboto, sans-serif;
  font-size: 0.75rem !important;
  font-weight: 500;
  height: 18px !important;
}

.event-meta {
  display: inline;
  font: inherit;
  font-weight: inherit;
  text-decoration: inherit;
}

.signup-sadge {
  width: 22px;
  height: 22px;
  display: block;
  position: relative;
  z-index: 1;
}
</style>
