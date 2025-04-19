<script setup lang="ts">
import $markdownToHtml from "@/plugins/markdownToHtml.ts";
import { type EventModel, type EventSignUpModel, type FormAnswer, defaultEventSignUp } from "@/models";
import { computed, nextTick, onMounted, ref, useTemplateRef } from "vue";
import { createEvent } from "ics";
import SignUpForm from "@/components/events/EventSignUpForm.vue";
import store from "@/plugins/store.ts";
import EventSignUpService from "@/services/EventSignUpService.ts";
import { $goto } from "@/plugins/goto";
import { useRoute } from "vue-router";
import { DateTime } from "luxon";

const props = defineProps({
  event: {
    type: Object as () => EventModel,
    required: true,
  },
  signUp: {
    type: Object as () => EventSignUpModel,
    default: () => defaultEventSignUp,
    required: false,
  },
});

const event = ref<EventModel>(props.event);

// Merge a default signUp object with props.signUp
const signUp = ref<EventSignUpModel>({
  ...defaultEventSignUp,
  eventId: event.value.id,
  ...props.signUp
});

const route = useRoute();
const expanded = ref(false);
const submitting = ref(false);
const eventElement = useTemplateRef("event");

// Store getters
const isMember = computed<boolean>(() => store.getters.isMember);
const isSmAndUp = computed<boolean>(() => store.getters.isSmAndUp);
const guestToken = computed<string | undefined>(() => store.getters.getGuestData?.token);
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn);

const signUpService = new EventSignUpService();

/**
 * Simple sign-up with no form
 */
async function submitSignUp() {
  submitting.value = true;
  if (signUp.value?.id) {
    await signUpService.updateSignUp(event.value.id!, signUp.value);
  } else {
    await signUpService.createSignUp(event.value.id!, signUp.value);
  }
  submitting.value = false;
  signUp.value.formAnswers = [];
}

/**
 * Cancel an existing sign-up
 */
async function removeSignUp() {
  if (isMember.value) {
    await signUpService.deleteSignUp(event.value.id!);
  } else {
    await signUpService.deleteSignUp(event.value.id!, guestToken.value);
  }
  delete signUp.value;
}

/**
 * If we navigated to a #hash that matches this event's id, scroll to it.
 */
onMounted(async () => {
  if (route.hash && event.value.id === Number(route.hash.replace("#", ""))) {
    await nextTick();
    eventElement.value?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
});

/**
 * Submit the sign-up form (with additional form fields).
 * Covers both logged-in and guest users.
 */
async function submitSignUpForm(
  eventId: number,
  payload: { answers: FormAnswer[]; guestData: any }
) {
  // Scroll up to the event
  eventElement.value?.scrollIntoView({ behavior: "smooth", block: "start" });

  if (isLoggedIn.value) {
    signUp.value = await signUpService.createSignUp(eventId, signUp.value);
  } else {
    // Save guest data in store
    store.commit("saveGuestData", payload.guestData.value);
  }
}

/**
 * Expand/collapse the sign-up form area
 */
function toggleExpanded() {
  if (!expanded.value) {
    eventElement.value?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
  expanded.value = !expanded.value;
}

/**
 * Open a link for the event location
 */
function findLocation() {
  if (event.value.location && event.value.location.toLowerCase().includes("discord")) {
    $goto("https://discord.gg/23YMFQy");
  } else if (event.value.location) {
    $goto(
      encodeURI("https://www.google.com/maps/search/?api=1&query=" + event.value.location)
    );
  }
}

/**
 * Generate an ICS file for the event and prompt download
 * Uses Luxon to build the date arrays for ICS in UTC.
 */
function downloadIcs() {
  const start = DateTime.fromISO(event.value.startTime).toUTC();
  const end = DateTime.fromISO(event.value.endTime).toUTC();

  createEvent(
    {
      title: event.value.title,
      description: event.value.description,
      location: event.value.location,
      start: [start.year, start.month, start.day, start.hour, start.minute],
      end: [end.year, end.month, end.day, end.hour, end.minute],
      // If you want ICS to treat them as local times, you could omit the "toUTC()" part above,
      // but ICS typically expects a time + timezone or a UTC time.
    },
    (error, value) => {
      if (error) {
        console.error(error);
        return;
      }
      const element = document.createElement("a");
      element.setAttribute(
        "href",
        "data:text/plain;charset=utf-8," + encodeURIComponent(value || "")
      );
      element.setAttribute("download", `${event.value.title}.ics`);
      element.style.display = "none";
      document.body.appendChild(element);
      element.click();
      document.body.removeChild(element);
    }
  );
}

/**
 * Copy a shareable link (with hash) to the clipboard
 */
async function copyShareLink() {
  const url = `${window.location.origin}${window.location.pathname}#${event.value.id}`;
  await navigator.clipboard.writeText(url);
  store.commit("setStatusSnackbarMessage", `Link for ${event.value.title} copied to clipboard`);
}

/**
 * Format event times with Luxon.
 * For example: "Monday, 5 June 10:00 - Monday, 5 June 12:00"
 */
function formatEventTime() {
  const startTime = DateTime.fromISO(event.value.startTime);
  const endTime = DateTime.fromISO(event.value.endTime);

  let result = "";

  // e.g. "Monday, 5 June, 10:00"
  result += startTime.toLocaleString({
    weekday: "long",
    day: "numeric",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
  });

  result += " - ";

  // If it ends on a different date, show "Monday, 5 June" again
  if (startTime.day !== endTime.day || startTime.month !== endTime.month || startTime.year !== endTime.year) {
    result += endTime.toLocaleString({
      weekday: "long",
      day: "numeric",
      month: "long",
    });
    result += " at ";
  }

  // e.g. "12:00"
  result += endTime.toLocaleString({
    hour: "2-digit",
    minute: "2-digit",
  });

  return result;
}
</script>

<template v-if="event.id">
  <v-list-item
    ref="event"
    :style="{
      'background-image': !event.banner
        ? ''
        : $vuetify.theme.global.current.dark
          ? `linear-gradient(to bottom, rgba(0,0,0,0.7), rgba(0,0,0,0.7)), url(${event.banner})`
          : `linear-gradient(to bottom, rgba(255,255,255,0.9), rgba(255,255,255,0.9)), url(${event.banner})`
    }"
    class="py-4"
    style="background-size: cover; background-position: center; min-height: 240px;"
  >
    <v-list-item-title class="text-h4">
      {{ event.title }}
    </v-list-item-title>

    <div
      class="text-subtitle-2 mb-2"
      style="opacity: var(--v-medium-emphasis-opacity);"
    >
      {{ event.location }} <br>
      {{ formatEventTime() }} <br>
      {{ event.membersOnly ? 'Members only' : '' }}
    </div>

    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-html="event.description ? $markdownToHtml(event.description) : 'No description...'" />

    <v-container class="fill-height pa-0">
      <v-row style="margin-right: -10px">
        <!-- Status label for signups (if needed) -->
        <v-col
          v-if="event.signUp && isSmAndUp && isLoggedIn"
          align-self="center"
        >
          <v-row>
            <p
              class="ml-2 mb-0 text-right"
              style="width: 100px"
            >
              <span v-if="submitting">Submitting...</span>
              <span v-else-if="signUp !== undefined">Signed up!</span>
              <span v-else-if="event.signUp">Not signed up</span>
            </p>
          </v-row>
        </v-col>

        <!-- Buttons on the right -->
        <v-col class="pa-0">
          <template v-if="event.signUp">
            <!-- Simple signup with no form -->
            <v-row v-if="isLoggedIn && !event.signUpForm?.length && signUp?.id">
              <v-tooltip
                location="left"
                text="Cancel sign-up"
              >
                <template #activator="{ props: tooltipProps }">
                  <v-btn
                    icon="mdi-checkbox-marked"
                    variant="plain"
                    :loading="submitting"
                    :disabled="event.membersOnly && !isMember"
                    v-bind="tooltipProps"
                    @click="removeSignUp()"
                  />
                </template>
              </v-tooltip>
            </v-row>

            <v-row v-else-if="isLoggedIn && !event.signUpForm?.length && !signUp?.id">
              <v-tooltip
                location="left"
                text="Sign Up"
              >
                <template #activator="{ props: tooltipProps }">
                  <v-btn
                    icon="mdi-checkbox-blank"
                    variant="plain"
                    :loading="submitting"
                    :disabled="event.membersOnly && !isMember"
                    v-bind="tooltipProps"
                    @click="submitSignUp()"
                  />
                </template>
              </v-tooltip>
            </v-row>

            <!-- SignUp form-based events -->
            <template v-else-if="event.signUpForm && (isLoggedIn || !event.membersOnly)">
              <!-- Cancel sign-up if already signed up -->
              <v-row>
                <v-tooltip
                  v-if="isLoggedIn && signUp !== undefined"
                  location="left"
                  text="Cancel sign-up"
                >
                  <template #activator="{ props: tooltipProps }">
                    <v-btn
                      icon="mdi-close"
                      variant="plain"
                      :loading="submitting"
                      :disabled="event.membersOnly && !isMember"
                      v-bind="tooltipProps"
                      @click="removeSignUp()"
                    />
                  </template>
                </v-tooltip>
              </v-row>

              <!-- Toggle sign-up form (edit or fill) -->
              <v-row>
                <v-tooltip
                  location="left"
                  :text="
                    signUp?.id
                      ? 'Edit sign-up form'
                      : expanded
                        ? 'Fill in sign-up form'
                        : 'Cancel filling in sign-up form'
                  "
                >
                  <template #activator="{ props: tooltipProps }">
                    <v-btn
                      icon="mdi-list-status"
                      variant="plain"
                      :loading="submitting"
                      :disabled="event.membersOnly && !isMember"
                      v-bind="tooltipProps"
                      @click="toggleExpanded()"
                    />
                  </template>
                </v-tooltip>
              </v-row>
            </template>
          </template>

          <!-- Location and share links -->
          <v-row>
            <v-tooltip
              text="Find location"
              location="left"
            >
              <template #activator="{ props: tooltipProps }">
                <v-btn
                  icon="mdi-google-maps"
                  variant="plain"
                  v-bind="tooltipProps"
                  @click="findLocation()"
                />
              </template>
            </v-tooltip>
          </v-row>
          <v-row>
            <v-tooltip
              text="Add to your calendar"
              location="left"
            >
              <template #activator="{ props: tooltipProps }">
                <v-btn
                  icon="mdi-calendar"
                  variant="plain"
                  v-bind="tooltipProps"
                  @click="downloadIcs()"
                />
              </template>
            </v-tooltip>
          </v-row>
          <v-row>
            <v-tooltip
              text="Copy share link"
              location="left"
            >
              <template #activator="{ props: tooltipProps }">
                <v-btn
                  icon="mdi-share-variant"
                  variant="plain"
                  v-bind="tooltipProps"
                  @click="copyShareLink()"
                />
              </template>
            </v-tooltip>
          </v-row>
        </v-col>
      </v-row>
    </v-container>

    <!-- If expanded, show the sign-up form -->
    <v-expand-transition :key="event.id">
      <div
        v-if="expanded"
        class="form-border mx-auto rounded-b"
      >
        <sign-up-form
          :initial-form-answers="signUp?.formAnswers"
          :event="event"
          class="form mx-auto"
          :show-guest-form="!isLoggedIn"
          @submit="({ answers, guestData }) => submitSignUpForm(event.id as number, { answers, guestData })"
        />
      </div>
    </v-expand-transition>
  </v-list-item>
</template>
