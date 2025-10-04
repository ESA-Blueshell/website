<script lang="ts" setup>
import {ref, watch} from "vue"
import {Field, Form, type FormContext} from "vee-validate"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {DateTime} from "luxon"
import {type AdvancedCommittee, createEvent, type Event, findCommittees, updateEvent} from "@/lib"
import router from "@/plugins/router.ts"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import SurveyEdit from "@/components/survey/SurveyEdit.vue"

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
    approved: false,
    membersOnly: false,
    signUp: false,
    banner: undefined,
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

const hadSignUp = ref<boolean>(event.value.signUp || false)
const oldEnableSignUpForm = ref<boolean>(!!event.value.signUpForm || false)

// Committees
const committees = ref<AdvancedCommittee[]>([])
findCommittees()
  .then((response) => (committees.value = response.data as AdvancedCommittee[] ?? []))
  .catch(() => (committees.value = []))

// Compute for the end date field (only if user checks "same start & end date")
const sameEndDate = ref(true)

function toISO({date, time, dateTime}: {
  date?: string
  time?: string
  dateTime?: string
}): string {
  if (dateTime) {
    const dt = DateTime.fromISO(dateTime)
    date = date ?? dt.toFormat("yyyy-MM-dd")
    time = time ?? dt.toFormat("HH:mm")
  }

  if (!date && !time) return ""

  return (
    DateTime.fromFormat(`${date ?? ""} ${time ?? ""}`.trim(), "yyyy-MM-dd HH:mm")
      .toISO() ?? ""
  )
}

watch([event, sameEndDate], () => {
  if (sameEndDate.value) {
    const time = DateTime.fromISO(event.value.endTime).toFormat("HH:mm")
    const dateTime = DateTime.fromISO(event.value.startTime).toISO()!
    event.value.endTime = toISO({time, dateTime})
  }
})

const formRef = ref<FormContext>()
const submitting = ref(false)
const signUpForm = ref<InstanceType<typeof SurveyEdit> | null>(null)
const enableSignUpForm = ref<boolean>(!!props.initialEvent?.signUpForm)

async function submit() {
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
            rules="required"
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
      <v-row class="mb-8">
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

      <!-- Checkboxes: sameEndDate, membersOnly, approved -->
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
            v-model="event.approved"
            name="approved"
          >
            <v-checkbox
              :model-value="value"
              :error-messages="errors"
              label="Approved"
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
            v-model="event.startTime"
            name="startDate"
            rules="required"
          >
            <v-text-field
              :model-value="DateTime.fromISO(value).toFormat('yyyy-MM-dd')"
              label="Start date"
              prepend-icon="mdi-calendar"
              type="date"
              :error-messages="errors"
              @update:model-value="(date: string) => handleChange(toISO({date, dateTime: event.endTime}))"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.startTime"
            name="startTime"
            rules="required"
          >
            <v-text-field
              :model-value="DateTime.fromISO(value).toFormat('HH:mm')"
              label="Start time"
              prepend-icon="mdi-clock"
              type="time"
              :error-messages="errors"
              @update:model-value="(time: string) => handleChange(toISO({time, dateTime: event.startTime}))"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.endTime"
            name="endDate"
            rules="required|dateTimeAfter:@startDate"
          >
            <v-text-field
              :disabled="sameEndDate"
              :model-value="DateTime.fromISO(value).toFormat('yyyy-MM-dd')"
              label="End date"
              prepend-icon="mdi-calendar"
              type="date"
              :error-messages="errors"
              @update:model-value="(date: string) => handleChange(toISO({date, dateTime: event.endTime}))"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.endTime"
            name="endTime"
            :rules="`required|dateTimeAfter:@startTime`"
          >
            <v-text-field
              :model-value="DateTime.fromISO(value).toFormat('HH:mm')"
              label="End time"
              prepend-icon="mdi-clock"
              type="time"
              :error-messages="errors"
              @update:model-value="(time: string) => handleChange(toISO({time, dateTime: event.endTime}))"
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
            v-model="enableSignUpForm"
            name="enableSignUpForm"
          >
            <v-checkbox
              :model-value="value"
              hide-details
              label="Enable sign-up form"
              @update:model-value="(enable: boolean) => {event.signUp = enable; handleChange(enable)}"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row v-if="enableSignUpForm">
        <v-col>
          <Field
            v-slot="{ errors, handleChange }"
            v-model="event.signUpForm"
            rules="notEmpty"
            name="signUpForm"
          >
            <survey-edit
              v-model="event.signUpForm"
              :error-messages="errors"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <!-- Warning if toggling sign-ups off -->
      <v-expand-transition>
        <v-alert
          v-if="(hadSignUp && !event.signUp) || (oldEnableSignUpForm && !event.signUpForm)"
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
    <v-row>
      <v-col cols="12">
        <v-btn
          :loading="submitting"
          block
          class="mt-8 mx-auto"
          color="primary"
          @click="submit"
        >
          Submit event
        </v-btn>
      </v-col>
    </v-row>
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
