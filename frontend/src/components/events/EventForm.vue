<script setup lang="ts">
import {ref, computed} from 'vue';
import EventSignUpFormEdit from '@/components/events/EventSignUpFormEdit.vue';
import {CommitteeService} from '@/services';
import {type CommitteeModel, type EventModel} from '@/models';
import {EventService} from '@/services';
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts";
import {useStore} from 'vuex';
import {DateTime} from 'luxon';
import type {VForm} from "vuetify/components";

const props = defineProps({
  initialEvent: {
    type: Object as () => EventModel,
    default: () => null
  },
  hasPromo: {
    type: Boolean,
    default: false
  }
});

const emits = defineEmits(['submit', 'title', 'success']);

const store = useStore();
const eventService = new EventService();
const committeeService = new CommitteeService();

const eventForm = ref<VForm>();
const signUpForm = ref(null);
const sameEndDate = ref(true);
const valid = ref(true);
const submitting = ref(false);

// --------------------
// 1) Initialize event
// --------------------
function getDefaultEvent(): EventModel {
  return {
    id: undefined,
    type: 'EventDTO',
    title: '',
    location: '',
    description: '',
    startTime: '',
    endTime: '',
    memberPrice: 0,
    publicPrice: 0,
    visible: false,
    membersOnly: false,
    signUp: false,
    banner: undefined,
    signUpForm: [],
    committeeId: undefined,
    committee: undefined
  };
}

function initializeEvent(): EventModel {
  return {
    ...getDefaultEvent(),
    ...(props.initialEvent || {}),
  };
}

const event = ref<EventModel>(initializeEvent());

// -------------------------------------------------------------
// 2) Convert existing ISO date/time → separate date + time vars
// -------------------------------------------------------------
const startDateTime = props.initialEvent.startTime
  ? DateTime.fromISO(props.initialEvent.startTime)
  : DateTime.local();

const endDateTime = props.initialEvent.endTime
  ? DateTime.fromISO(props.initialEvent.endTime)
  : DateTime.local();

// We store date/time as strings so they can bind to <v-text-field type="date/time">
const startDate = ref(startDateTime.toFormat('yyyy-MM-dd'));
const startTime = ref(startDateTime.toFormat('HH:mm'));
const endDate = ref(endDateTime.toFormat('yyyy-MM-dd'));
const endTime = ref(endDateTime.toFormat('HH:mm'));

// We also track whether the user previously had signUp or signUpForm
// so we can warn them about removing sign-ups if toggled off.
const enableSignupForm = ref<boolean>(!!(event.value.signUpForm && event.value.signUpForm.length));

const wasPublic = ref<boolean>(event.value.visible || false);
const hadSignUp = ref<boolean>(event.value.signUp || false);
const oldEnableSignUpForm = ref<boolean>(enableSignupForm.value);

// Committees
const committees = ref<CommitteeModel[]>([]);
committeeService.getCommittees(true).then((response) => (committees.value = response));

// Price rules
const priceRules = [
  (v: string) => !isNaN(Number(v)) || 'Price must be a number',
  (v: string) => Number(v) < 100 || "That's a little much no?",
  (v: string) => Number(v) >= 0 || 'Negative prices? That would be weird',
];

// Compute for the end date field (only if user unchecks "sameEndDate")
const endDateDisplay = computed({
  get: () => (sameEndDate.value ? startDate.value : endDate.value),
  set: (value: string) => {
    endDate.value = value;
  },
});

// ------------------------------------
// 3) On submit → combine date + time
// ------------------------------------
async function submit() {
  if (sameEndDate.value) {
    // If user checked "same start and end date"
    endDate.value = startDate.value;
  }

  const result = await eventForm.value?.validate();
  if (!result || !result.valid) return;

  submitting.value = true;

  try {
    // Clone our event object
    const payload = {...event.value};

    // Combine date + time -> Luxon -> ISO
    payload.startTime = DateTime.fromFormat(
      `${startDate.value} ${startTime.value}`,
      'yyyy-MM-dd HH:mm'
    ).toISO() as string;

    payload.endTime = DateTime.fromFormat(
      `${endDate.value} ${endTime.value}`,
      'yyyy-MM-dd HH:mm'
    ).toISO() as string;

    // signUpForm is already an array → keep it
    // (If you need to send JSON string, do: JSON.stringify(event.value.signUpForm))
    payload.signUpForm = event.value.signUpForm ?? [];

    // If we have a promo image as a FileModel, convert it to base64
    // if (event.value.banner && event.value.banner instanceof FileModel) {
    //   const base64Image = await toBase64(event.value.banner);
    //   const fileExtension = '.' + event.value.banner.name.split('.').pop();
    //   (payload as any).base64Image = base64Image;
    //   (payload as any).fileExtension = fileExtension;
    // }

    // console.log('payload:', payload);
    // console.log('initialEvent:', props.initialEvent);

    // Correct create vs. update logic:
    if (!payload.id) {
      // No ID => create
      await eventService.createEvent(payload);
    } else {
      // Has ID => update
      await eventService.updateEvent(payload.id, payload);
    }

    emits('success');
  } catch (e: unknown) {
    $handleNetworkError(e);
  } finally {
    submitting.value = false;
  }
}

// function toBase64(file: FileModel) {
//   return new Promise<string>((resolve, reject) => {
//     const reader = new FileReader();
//     reader.readAsDataURL(file);
//     reader.onload = () => {
//       const base64 = (reader.result as string)
//         .replace('data:', '')
//         .replace(/^.+,/, '');
//       resolve(base64);
//     };
//     reader.onerror = (error) => reject(error);
//   });
// }

// Toggles
function toggleSignUp() {
  event.value.signUp = !event.value.signUp;
  // If user disables signUp, also disable signUpForm
  if (!event.value.signUp) {
    enableSignupForm.value = false;
  }
}

function toggleSignUpForm() {
  enableSignupForm.value = !enableSignupForm.value;
  if (enableSignupForm.value) {
    // If enabling form, ensure signUp is also enabled
    event.value.signUp = true;
  }
}
</script>

<template>
  <v-form
    ref="eventForm"
    v-model="valid"
  >
    <v-container style="padding: 0;">
      <!-- Title + Location -->
      <v-row>
        <v-col
          cols="12"
          lg="8"
        >
          <v-text-field
            ref="title"
            v-model="event.title"
            :rules="[v => !!v || 'Title is required']"
            label="EventModel name"
            required
            @update:model-value="emits('title', event.title)"
          />
        </v-col>
        <v-col
          cols="12"
          lg="4"
        >
          <v-text-field
            ref="location"
            v-model="event.location"
            :rules="[v => (!!v || !event.visible) || 'Location is required for public events']"
            label="Location"
            required
          />
        </v-col>
      </v-row>

      <!-- Description -->
      <v-row>
        <v-col>
          <v-textarea
            ref="description"
            v-model="event.description"
            :rules="[v => !!v || 'Description is required']"
            label="Description"
            variant="outlined"
            hide-details
            required
          />
        </v-col>
      </v-row>

      <!-- Prices -->
      <v-row>
        <v-col>
          <v-text-field
            ref="memberPrice"
            v-model="event.memberPrice"
            :rules="priceRules"
            label="Price for members"
            prepend-icon="mdi-currency-eur"
            required
          />
        </v-col>
        <v-col>
          <v-text-field
            ref="publicPrice"
            v-model="event.publicPrice"
            :rules="priceRules"
            label="Price for non-members"
            prepend-icon="mdi-currency-eur"
            required
          />
        </v-col>
      </v-row>

      <!-- Checkboxes: sameEndDate, membersOnly, visible -->
      <v-row>
        <v-col>
          <v-checkbox
            v-model="sameEndDate"
            label="Same start and end date"
          />
        </v-col>
        <v-col>
          <v-checkbox
            v-model="event.membersOnly"
            label="Members only"
          />
        </v-col>
        <v-col>
          <v-checkbox
            v-model="event.visible"
            :rules="[v => !v || wasPublic || store.getters.isBoard || 'Sorry, only board members can make this public']"
            label="Make public"
          />
        </v-col>
      </v-row>

      <!-- Date/Time: Start -->
      <v-row>
        <v-col>
          <v-text-field
            v-model="startDate"
            :rules="[v => (!!v || !event.visible) || 'Start date is required for public events']"
            label="Start date"
            prepend-icon="mdi-calendar"
            type="date"
          />
        </v-col>
        <v-col>
          <v-text-field
            v-model="startTime"
            :rules="[v => (!!v || !event.visible) || 'Start time is required for public events']"
            label="Start time"
            prepend-icon="mdi-clock"
            type="time"
          />
        </v-col>
      </v-row>

      <!-- Date/Time: End -->
      <v-row>
        <v-col>
          <v-text-field
            v-model="endDateDisplay"
            :disabled="sameEndDate"
            :rules="[
              v => (!!v || !event.visible) || 'End date is required for public events',
              v => (!!v && new Date(startDate) <= new Date(endDateDisplay)) || 'End date cannot be earlier than start date'
            ]"
            label="End date"
            prepend-icon="mdi-calendar"
            type="date"
          />
        </v-col>
        <v-col>
          <v-text-field
            v-model="endTime"
            :rules="[
              v => (!!v || !event.visible) || 'End time is required for public events',
              v => (!!v && new Date(`${startDate} ${startTime}`) < new Date(`${endDateDisplay} ${v}`)) || 'EventModel must end after it starts'
            ]"
            label="End time"
            prepend-icon="mdi-clock"
            type="time"
          />
        </v-col>
      </v-row>

      <!-- CommitteeModel + FileModel Input -->
      <v-row>
        <v-col>
          <v-select
            v-model="event.committeeId"
            :disabled="!committees"
            :items="committees"
            :rules="[v => !!v || 'A representative committee is required']"
            item-title="name"
            item-value="id"
            prepend-icon="mdi-account-group"
            label="Representative committee"
          />
        </v-col>
        <v-col>
          <v-file-input
            v-model="event.banner"
            accept="image/jpeg"
            label="Promo image (Max 2MB)"
            persistent-hint
            :hint="hasPromo ? 'This event already has a promo image; only choose a file if you want to overwrite it' : undefined"
            show-size
            clearable
          />
        </v-col>
      </v-row>

      <!-- Sign-up toggles + signUpForm -->
      <v-row>
        <v-col>
          <v-checkbox
            :model-value="event.signUp"
            label="Enable sign-up"
            hide-details
            @click="toggleSignUp"
          />
        </v-col>
        <v-col>
          <v-checkbox
            :model-value="enableSignupForm"
            label="Enable sign-up form"
            hide-details
            @click="toggleSignUpForm"
          />
        </v-col>
      </v-row>
      <v-row v-if="enableSignupForm">
        <v-col>
          <event-sign-up-form-edit
            ref="signUpForm"
            :initial-form="event.signUpForm"
            @change="newForm => (event.signUpForm = newForm)"
          />
        </v-col>
      </v-row>

      <!-- Warning if toggling sign-ups off -->
      <v-expand-transition>
        <v-alert
          v-if="(hadSignUp && !event.signUp) || (oldEnableSignUpForm && !enableSignupForm)"
          type="warning"
          prominent
          variant="outlined"
          class="mt-4 mx-3"
        >
          Woah there! Looks like you changed sign-up settings. Once you submit, any existing sign-ups
          <b>will be removed</b>!
        </v-alert>
      </v-expand-transition>
    </v-container>

    <!-- Submit button -->
    <v-btn
      :loading="submitting"
      block
      class="mt-4 mx-3"
      color="primary"
      @click="submit"
    >
      Submit event
    </v-btn>
  </v-form>
</template>
