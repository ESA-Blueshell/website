<script setup lang="ts">
import $markdownToHtml from "@/plugins/markdownToHtml.ts";
import { computed, nextTick, onMounted, ref } from "vue";
import { createEvent as createIcsEvent } from "ics";
import SignUpForm from "@/components/events/EventSignUpForm.vue";
import store from "@/plugins/store.ts";
import { $goto } from "@/plugins/goto";
import { useRoute } from "vue-router";
import { DateTime } from "luxon";
import {
  updateEventSignUp,
  createEventSignup,
  deleteEventSignup,
  type EventDto,
  type EventSignUpDto
} from "@/lib/blueshell";

const props = defineProps({
  event: {
    type: Object as () => EventDto,
    required: true,
  },
  signUp: {
    type: Object as () => EventSignUpDto,
    default: () =>
      ({
        id: undefined,
        eventId: undefined,
        formAnswers: [],
      } as EventSignUpDto),
    required: false,
  },
});

const event = ref<EventDto>(props.event);

const signUp = ref<EventSignUpDto>({
  id: props.signUp?.id,
  eventId: props.event.id,
  formAnswers: props.signUp?.formAnswers ?? [],
  ...props.signUp,
});

const route = useRoute();
const expanded = ref(false);
const submitting = ref(false);
const eventElement = ref<HTMLElement | null>(null);

const isMember = computed<boolean>(() => store.getters.isMember);
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn);

async function submitSignUp() {
  submitting.value = true;
  try {
    if (signUp.value?.id) {
      await updateEventSignUp({
        path: { eventId: event.value.id as number },
        body: signUp.value,
      });
    } else {
      await createEventSignup({
        path: { id: event.value.id as number },
        body: {
          ...signUp.value,
          eventId: event.value.id as number,
        },
      });
    }
    signUp.value.formAnswers = [];
  } finally {
    submitting.value = false;
  }
}

async function removeSignUp() {
  if (signUp.value?.id !== undefined) {
    await deleteEventSignup({
      path: { eventSignupId: signUp.value.id as number },
    });
  }
  signUp.value = {
    id: undefined,
    eventId: event.value.id,
    formAnswers: [],
  } as EventSignUpDto;
}

onMounted(async () => {
  if (route.hash && event.value.id === Number(route.hash.replace("#", ""))) {
    await nextTick();
    eventElement.value?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
});

async function submitSignUpForm(
  eventId: number,
  payload: { answers: any; guestData: any }
) {
  eventElement.value?.scrollIntoView({ behavior: "smooth", block: "start" });

  signUp.value.formAnswers = payload.answers ?? [];

  if (isLoggedIn.value) {
    if (signUp.value?.id) {
      await updateEventSignUp({
        path: { eventId },
        body: signUp.value,
      });
    } else {
      await createEventSignup({
        path: { id: eventId },
        body: { ...signUp.value, eventId },
      });
    }
  } else {
    store.commit("saveGuestData", payload.guestData?.value ?? payload.guestData);
    await createEventSignup({
      path: { id: eventId },
      body: { ...signUp.value, eventId },
    });
  }
}

function toggleExpanded() {
  if (!expanded.value) {
    eventElement.value?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
  expanded.value = !expanded.value;
}

function findLocation() {
  if (event.value.location && event.value.location.toLowerCase().includes("discord")) {
    $goto("https://discord.gg/23YMFQy");
  } else if (event.value.location) {
    $goto(
      encodeURI("https://www.google.com/maps/search/?api=1&query=" + event.value.location)
    );
  }
}

function downloadIcs() {
  const start = DateTime.fromISO(event.value.startTime).toUTC();
  const end = DateTime.fromISO(event.value.endTime).toUTC();

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

async function copyShareLink() {
  const url = `${window.location.origin}${window.location.pathname}#${event.value.id}`;
  await navigator.clipboard.writeText(url);
  store.commit("setStatusSnackbarMessage", `Link for ${event.value.title} copied to clipboard`);
}

function formatEventTime() {
  const startTime = DateTime.fromISO(event.value.startTime);
  const endTime = DateTime.fromISO(event.value.endTime);

  let result = "";

  result += startTime.toLocaleString({
    weekday: "long",
    day: "numeric",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
  });

  result += " - ";

  if (
    startTime.day !== endTime.day ||
    startTime.month !== endTime.month ||
    startTime.year !== endTime.year
  ) {
    result += endTime.toLocaleString({
      weekday: "long",
      day: "numeric",
      month: "long",
    });
    result += " at ";
  }

  result += endTime.toLocaleString({
    hour: "2-digit",
    minute: "2-digit",
  });

  return result;
}
</script>

<template v-if="event.id">
  <v-list-item
    :style="{
      'background-image': !event.banner
        ? ''
        : $vuetify.theme.global.current.dark
          ? `linear-gradient(to bottom, rgba(0,0,0,0.7), rgba(0,0,0,0.7)), url(${event.banner})`
          : `linear-gradient(to bottom, rgba(255,255,255,0.9), rgba(255,255,255,0.9)), url(${event.banner})`
    }"
    rounded="sm"
    class="py-4"
    style="background-size: cover; background-position: center; min-height: 240px;"
  >
    <div ref="eventElement">
      <v-container>
        <v-row
          no-gutters
          align="start"
        >
          <v-col>
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

            <div v-html="event.description ? $markdownToHtml(event.description) : 'No description...'" />
          </v-col>
          <v-col
            class="pa-0"
            cols="auto"
          >
            <template v-if="event.signUp">
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

              <template v-else-if="event.signUpForm && (isLoggedIn || !event.membersOnly)">
                <v-row>
                  <v-tooltip
                    v-if="isLoggedIn && signUp?.id !== undefined"
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
        <v-row>
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
        </v-row>
      </v-container>
    </div>
  </v-list-item>
</template>

<style scoped>
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
