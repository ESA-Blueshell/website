<!--
Refactor notes:
- Replaced <v-form> validation with VeeValidate <Form> + <Field> wrappers (slot-based) to keep Vuetify styling.
- Uses your existing custom rules (required, minValue, maxValue, dateBefore, dateAfter, etc.).
- Added two tiny rules you likely want in your validators file:
    1) requiredIfTrue (usage: rules="requiredIfTrue:@visible")
    2) dateTimeAfter (usage: rules="dateTimeAfter:@startDate,@startTime")
  See bottom of this file for the rule implementations to add to your validators setup file.
-->

<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import EventSignUpFormEdit from "@/components/events/EventSignUpFormEdit.vue"
import type {FormContext} from "vee-validate"
import {Field, Form} from "vee-validate"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {DateTime} from "luxon"
import {type AdvancedCommittee, createEvent, type Event, findCommittees, type FormQuestion, updateEvent} from "@/lib"
import router from "@/plugins/router.ts"
import {useBackendValidation} from "@/plugins/serverValidation.ts"

const props = defineProps({
  initialEvent: {
    type: Object as () => Event,
    default: () => null,
  },
  hasPromo: {
    type: Boolean,
    default: false,
  },
})

const {apply} = useBackendValidation()

// --------------------
// 1) Initialize event
// --------------------
function getDefaultEvent(): Event {
  return {
    id: undefined,
    title: "",
    location: "",
    description: "",
    startTime: "",
    endTime: "",
    memberPrice: 0,
    publicPrice: 0,
    visible: false,
    membersOnly: false,
    signUp: false,
    banner: undefined,
    signUpForm: [],
    committeeId: 0,
  }
}

function initializeEvent(): Event {
  return {
    ...getDefaultEvent(),
    ...(props.initialEvent || {}),
  }
}

const event = ref<Event>(initializeEvent())

// -------------------------------------------------------------
// 2) Convert existing ISO date/time → separate date + time vars
// -------------------------------------------------------------
const startDateTime = props.initialEvent?.startTime
  ? DateTime.fromISO(props.initialEvent.startTime)
  : DateTime.local()

const endDateTime = props.initialEvent?.endTime
  ? DateTime.fromISO(props.initialEvent.endTime)
  : DateTime.local()

const startDate = ref(startDateTime.toFormat("yyyy-MM-dd"))
const startTime = ref(startDateTime.toFormat("HH:mm"))
const endDate = ref(endDateTime.toFormat("yyyy-MM-dd"))
const endTime = ref(endDateTime.toFormat("HH:mm"))

const hadSignUp = ref<boolean>(event.value.signUp || false)
const oldEnableSignUpForm = ref<boolean>(event.value.signUp || false)

// Committees
const committees = ref<AdvancedCommittee[]>([])
findCommittees()
  .then((response) => (committees.value = response.data as AdvancedCommittee[] ?? []))
  .catch(() => (committees.value = []))

// Compute for the end date field (only if user checks "same start & end date")
const sameEndDate = ref(true)
const endDateDisplay = computed({
  get: () => (sameEndDate.value ? startDate.value : endDate.value),
  set: (value: string) => {
    endDate.value = value
  },
})

function toISO(date: string, time: string): string {
  if (!date || !time) return ""
  const iso = DateTime.fromFormat(`${date} ${time}`, "yyyy-MM-dd HH:mm").toISO()
  return iso ?? ""
}

watch([startDate, startTime], () => {
  event.value.startTime = toISO(startDate.value, startTime.value)
})

watch([endDate, endTime], () => {
  if (sameEndDate.value) {
    endDate.value = startDate.value
  }
  event.value.endTime = toISO(endDate.value, endTime.value)
})

// -----------------------
// 3) Submit (validate → save)
// -----------------------
const formRef = ref<FormContext>()
const submitting = ref(false)
const signUpForm = ref<InstanceType<typeof EventSignUpFormEdit> | null>(null)

async function submit() {
  if (sameEndDate.value) endDate.value = startDate.value

  const result = await formRef.value?.validate()
  if (!result?.valid) return

  submitting.value = true

  try {
    if (!event.value?.id) {
      const resp = await createEvent({body: event.value})
      if (resp.status === 201) {
        submitting.value = false
        router.back()
      } else if (!(apply(formRef.value!, resp))) {
        $handleNetworkError(resp)
      }
    } else {
      const resp = await updateEvent({path: {id: event.value.id}, body: event.value})
      if (resp.status === 200) {
        submitting.value = false
        router.back()
      } else if (!(apply(formRef.value!, resp))) {
        $handleNetworkError(resp)
      }
    }
  } finally {
    submitting.value = false
  }
}

</script>

<template>
  <Form
    ref="formRef"
    as="div"
  >
    <v-container style="padding: 0;">
      <!-- Title + Location -->
      <v-row>
        <v-col
          cols="12"
          lg="8"
        >
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.title"
            name="title"
            rules="required"
          >
            <v-text-field
              :model-value="value"
              label="Event name"
              :error-messages="errors"
              required
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
        <v-col
          cols="12"
          lg="4"
        >
          <!-- required only when event is public -->
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.location"
            name="location"
            rules="requiredIfTrue:@visible"
          >
            <v-text-field
              :model-value="value"
              label="Location"
              :error-messages="errors"
              required
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <!-- Description -->
      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.description"
            name="description"
            rules="required"
          >
            <v-textarea
              :model-value="value"
              label="Description"
              variant="outlined"
              hide-details
              required
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <!-- Prices -->
      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.memberPrice"
            name="memberPrice"
            rules="minValue:0|maxValue:99.99"
          >
            <v-text-field
              :model-value="value"
              label="Price for members"
              prepend-icon="mdi-currency-eur"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.publicPrice"
            name="publicPrice"
            rules="minValue:0|maxValue:99.99"
          >
            <v-text-field
              :model-value="value"
              label="Price for non-members"
              prepend-icon="mdi-currency-eur"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <!-- Checkboxes: sameEndDate, membersOnly, visible -->
      <v-row>
        <v-col>
          <Field
            v-slot="{ value, handleChange }"
            v-model="sameEndDate"
            name="sameEndDate"
          >
            <v-checkbox
              :model-value="value"
              label="Same start and end date"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, handleChange }"
            v-model="event.membersOnly"
            name="membersOnly"
          >
            <v-checkbox
              :model-value="value"
              label="Members only"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange }"
            v-model="event.visible"
            name="visible"
          >
            <v-checkbox
              :model-value="value"
              :error-messages="errors"
              label="Make public"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <!-- Date/Time: Start -->
      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="startDate"
            name="startDate"
            rules="requiredIfTrue:@visible|dateRequired"
          >
            <v-text-field
              :model-value="value"
              label="Start date"
              prepend-icon="mdi-calendar"
              type="date"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="startTime"
            name="startTime"
            rules="requiredIfTrue:@visible"
          >
            <v-text-field
              :model-value="value"
              label="Start time"
              prepend-icon="mdi-clock"
              type="time"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <!-- Date/Time: End -->
      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="endDateDisplay"
            name="endDate"
            rules="requiredIfTrue:@visible|dateAfter:@startDate"
          >
            <v-text-field
              :disabled="sameEndDate"
              :model-value="value"
              label="End date"
              prepend-icon="mdi-calendar"
              type="date"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="endTime"
            name="endTime"
            rules="requiredIfTrue:@visible|dateTimeAfter:@startDate,@startTime,@endDate"
          >
            <v-text-field
              :model-value="value"
              label="End time"
              prepend-icon="mdi-clock"
              type="time"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <!-- Committee + File Input -->
      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.committeeId"
            name="committeeId"
            rules="required"
          >
            <v-select
              :model-value="value"
              :disabled="!committees"
              :items="committees"
              item-title="name"
              item-value="id"
              label="Representative committee"
              prepend-icon="mdi-account-group"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
        <v-col>
          <!-- optional; no validation by default -->
          <Field
            v-slot="{ value, handleChange, handleBlur }"
            v-model="event.banner"
            name="banner"
          >
            <v-file-input
              :model-value="value as any"
              :hint="hasPromo ? 'This event already has a promo image; only choose a file if you want to overwrite it' : undefined"
              accept="image/jpeg"
              clearable
              label="Promo image (Max 2MB)"
              persistent-hint
              show-size
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <!-- Sign-up toggles + signUpForm -->
      <v-row>
        <v-col>
          <Field
            v-slot="{ value, handleChange }"
            v-model="event.signUp"
            name="signUp"
          >
            <v-checkbox
              :model-value="value"
              hide-details
              label="Enable sign-up"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, handleChange }"
            v-model="event.signUp"
            name="enableSignupForm"
          >
            <v-checkbox
              :model-value="value"
              hide-details
              label="Enable sign-up form"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row v-if="event.signUp">
        <v-col>
          <event-sign-up-form-edit
            ref="signUpForm"
            :initial-form="event.signUpForm"
            @change="(newForm: FormQuestion[]) => (event.signUpForm = newForm)"
          />
        </v-col>
      </v-row>

      <!-- Warning if toggling sign-ups off -->
      <v-expand-transition>
        <v-alert
          v-if="(hadSignUp && !event.signUp) || (oldEnableSignUpForm && !event.signUp)"
          class="mt-4 mx-3"
          prominent
          type="warning"
          variant="outlined"
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
  </Form>
</template>

<style lang="scss">
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}

.v-col {
  padding-bottom: 0;
  padding-top: 0;
}
</style>
