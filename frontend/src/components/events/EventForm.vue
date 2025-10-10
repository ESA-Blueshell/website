<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {defineRule, Field, Form, type FormContext} from "vee-validate"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {DateTime} from "luxon"
import {
  type AdvancedCommittee,
  createEvent,
  downloadEventBanner,
  type Event,
  type EventBanner,
  findCommittees,
  updateEvent,
  uploadEventBanner,
} from "@/lib"
import router from "@/plugins/router.ts"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import SurveyEdit from "@/components/survey/SurveyEdit.vue"
import MarkdownField from "@/components/MarkdownField.vue"
import {useStore} from "vuex"

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

function getDefaultEvent(): Event {
  return {
    id: undefined,
    title: "",
    location: "",
    description: "",
    startTime: DateTime.now().plus({days: 1}).toISO(),
    endTime: DateTime.now().plus({hours: 3}).toISO(),
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
const store = useStore()
const hadSignUp = ref<boolean>(event.value.signUp || false)
const oldEnableSignUpForm = ref<boolean>(!!event.value.signUpForm || false)

const committees = ref<AdvancedCommittee[]>([])
findCommittees()
  .then((response) => (committees.value = response.data as AdvancedCommittee[] ?? []))
  .catch(() => (committees.value = []))

const sameEndDate = ref(true)

function toISO({
                 date,
                 time,
                 dateTime,
               }: { date?: string; time?: string; dateTime?: string }): string {
  const base = dateTime ? DateTime.fromISO(dateTime) : null
  const hasValidBase = !!base && base.isValid

  const d = date ?? (hasValidBase ? base!.toFormat("yyyy-MM-dd") : undefined)
  const t = time ?? (hasValidBase ? base!.toFormat("HH:mm") : undefined)

  if (!d && !t) return ""

  if (d && t) {
    const dt = DateTime.fromFormat(`${d} ${t}`, "yyyy-MM-dd HH:mm")
    return dt.isValid ? dt.toISO()! : ""
  }

  if (d) {
    const dt = DateTime.fromFormat(d, "yyyy-MM-dd").set({
      hour: hasValidBase ? base!.hour : 0,
      minute: hasValidBase ? base!.minute : 0,
    })
    return dt.isValid ? dt.toISO()! : ""
  }

  if (t) {
    const ref = hasValidBase ? base! : DateTime.now()
    const [h, m] = t.split(":").map((n) => Number(n))
    const dt = ref.set({hour: h ?? 0, minute: m ?? 0})
    return dt.isValid ? dt.toISO()! : ""
  }

  return ""
}

function safeFormatISO(iso: string, fmt: string) {
  const dt = DateTime.fromISO(iso || "")
  return dt.isValid ? dt.toFormat(fmt) : ""
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
      console.log("on submit value is:", event.value.banner)
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

function getFirstFile(value: File | File[] | null | undefined): File | null {
  if (Array.isArray(value)) return value[0] ?? null
  return (value as File | null) ?? null
}

defineRule("fileSize", (value: File | File[] | null) => {
  const f = getFirstFile(value)
  if (!f) return true
  return f.size <= 2 * 1024 * 1024 || "Promo image must be ≤ 2MB"
})
const banner = ref<File | null>(null)

async function onBannerChange(val: File | File[] | null, handleChange: (v: File) => void) {
  const file = getFirstFile(val)
  if (!file) {
    banner.value = null
    event.value.banner = undefined
    return
  }

  const res = await formRef.value?.validateField("banner")
  if (!res?.valid) return

  const resp = await uploadEventBanner({
    body: {
      file,
    },
  })

  if (resp.status === 201) {
    event.value.banner = {file: resp.data!} as EventBanner
    handleChange(file)
  } else if (!apply(formRef.value!, resp)) {
    $handleNetworkError(resp)
  }
}

watch(
  () => event.value.signUp,
  (signUp) => {
    if (!signUp) {
      event.value.signUpForm = undefined
      enableSignUpForm.value = false
    }
  },
)

watch(
  enableSignUpForm,
  (on) => {
    if (!on) {
      event.value.signUpForm = undefined
    } else {
      event.value.signUp = true
    }
  },
)


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
    if (!blob) return

    banner.value = new File([blob], event.value.banner.file.name!, {
      type: blob.type || "application/octet-stream",
      lastModified: Date.now(),
    })
  } catch (e) {
    console.error("Failed to download event banner:", e)
  }
}

watch([() => event.value.startTime, () => event.value.endTime, sameEndDate], () => {
  if (!sameEndDate.value) return

  const end = DateTime.fromISO(event.value.endTime)
  const time = end.isValid ? end.toFormat("HH:mm") : "00:00"
  event.value.endTime = toISO({time, dateTime: event.value.startTime})
})

onMounted(loadBanner)
const initialJson = ref(JSON.stringify(event.value))
const isDirty = computed(() => JSON.stringify(event.value) !== initialJson.value)
const isBoard = computed((): boolean => store.getters.isBoard)

</script>

<template>
  <Form
    ref="formRef"
    as="div"
  >
    <v-container
      style="padding: 0;"
    >
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
              :error-messages="errors"
              :model-value="value"
              label="Event name"
              required
              @blur="handleBlur"
              @update:model-value="handleChange"
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
              :error-messages="errors"
              :model-value="value"
              label="Location"
              required
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row class="mb-8">
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.description"
            name="description"
            rules="required"
          >
            <markdown-field
              :error-messages="errors"
              :model-value="value"
              label="Description"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.memberPrice"
            name="memberPrice"
            rules="minValue:0|maxValue:99.99"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Price for members"
              prepend-icon="mdi-currency-eur"
              @blur="handleBlur"
              @update:model-value="handleChange"
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
              :error-messages="errors"
              :model-value="value"
              label="Price for non-members"
              prepend-icon="mdi-currency-eur"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

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
            v-if="isBoard"
            v-slot="{ value, errors, handleChange }"
            v-model="event.approved"
            name="approved"
          >
            <v-checkbox
              :error-messages="errors"
              :model-value="value"
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
              :error-messages="errors"
              :model-value="safeFormatISO(value, 'yyyy-MM-dd')"
              label="Start date"
              prepend-icon="mdi-calendar"
              type="date"
              @blur="handleBlur"
              @update:model-value="(date: string) => handleChange(toISO({date, dateTime: event.endTime}))"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.startTime"
            :rules="event.id ? 'required' : `required|dateTimeAfter:${DateTime.now().toISO()}`"
            name="startTime"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="safeFormatISO(value, 'HH:mm')"
              label="Start time"
              prepend-icon="mdi-clock"
              type="time"
              @blur="handleBlur"
              @update:model-value="(time: string) => handleChange(toISO({ time, dateTime: event.startTime }))"
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
              :error-messages="errors"
              :model-value="safeFormatISO(value, 'yyyy-MM-dd')"
              label="End date"
              prepend-icon="mdi-calendar"
              type="date"
              @blur="handleBlur"
              @update:model-value="(date: string) => handleChange(toISO({date, dateTime: event.endTime}))"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.endTime"
            name="endTime"
            rules="required|dateTimeAfter:@startTime"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="DateTime.fromISO(value).toFormat('HH:mm')"
              label="End time"
              prepend-icon="mdi-clock"
              type="time"
              @blur="handleBlur"
              @update:model-value="(time: string) => handleChange(toISO({time, dateTime: event.endTime}))"
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
              :disabled="!committees"
              :error-messages="errors"
              :items="committees"
              :model-value="value"
              item-title="name"
              item-value="id"
              label="Representative committee"
              prepend-icon="mdi-account-group"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="banner"
            name="banner"
            rules="fileSize"
          >
            <v-file-input
              :error-messages="errors"
              :model-value="value as File"
              accept="image/png, image/jpeg, image/jpg, image/webp, image/gif"
              clearable
              label="Promo image (Max 2MB)"
              show-size
              @blur="handleBlur"
              @update:model-value="(blob: File) => onBannerChange(blob, handleChange)"
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
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row v-if="enableSignUpForm">
        <v-col>
          <Field
            v-slot="{ errors, handleChange }"
            v-model="event.signUpForm"
            name="signUpForm"
            rules="notEmpty"
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

    <v-expand-transition class="mt-4">
      <v-alert
        v-if="isDirty && !isBoard"
        prominent
        type="warning"
        variant="outlined"
      >
        Making changes to this event will cause it to be hidden from the calendar until a board member has re-approved
        it.
      </v-alert>
    </v-expand-transition>

    <v-expand-transition class="mt-4">
      <v-alert
        v-if="event.id && DateTime.fromISO(event.startTime) < DateTime.now()"
        prominent
        type="error"
        variant="outlined"
      >
        It is not allowed to make changes to events which have already started.
      </v-alert>
    </v-expand-transition>

    <!-- Submit button -->
    <v-row>
      <v-col cols="12">
        <v-btn
          :disabled="event.id && DateTime.fromISO(event.startTime) < DateTime.now()"
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
