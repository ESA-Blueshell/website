<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import {defineRule, Field, Form, type FormContext} from "vee-validate"
import MarkdownField from "@/components/form/fields/MarkdownField.vue"
import SurveyForm from "@/components/form/SurveyForm.vue"
import {useStore} from "vuex"
import router from "@/plugins/router.ts"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {
  type AdvancedCommittee,
  createEvent,
  downloadEventBanner,
  type Event,
  type EventBanner,
  findCommittees,
  updateEvent,
  uploadEventBanner,
} from "@/services/api"

const props = defineProps({
  initialEvent: {type: Object as () => Event, default: () => null},
  hasPromo: {type: Boolean, default: false},
})

const store = useStore()
const {apply} = useBackendValidation()

function getDefaultEvent(): Event {
  return {
    id: undefined,
    title: "",
    location: "",
    description: "",
    startTime: DateTime.now().plus({days: 1}).toISO(),
    endTime: DateTime.now().plus({days: 1, hours: 3}).toISO(),
    memberPrice: 0,
    publicPrice: 0,
    approved: false,
    membersOnly: false,
    signUp: false,
    banner: undefined,
    committeeId: 0,
  }
}

const event = ref<Event>({...getDefaultEvent(), ...(props.initialEvent || {})})
const hadSignUp = ref<boolean>(!!event.value.signUp)
const oldEnableSignUpForm = ref<boolean>(!!event.value.signUpForm)
const committees = ref<AdvancedCommittee[]>([])
const sameEndDate = ref(true)

const formRef = ref<FormContext>()
const submitting = ref(false)
const enableSignUpForm = ref<boolean>(!!props.initialEvent?.signUpForm)

const isBoard = computed<boolean>(() => store.getters.isBoard)
const initialJson = ref(JSON.stringify(event.value))
const isDirty = computed(() => JSON.stringify(event.value) !== initialJson.value)

function safeFormatISO(iso: string, fmt: string) {
  const dt = DateTime.fromISO(iso || "")
  return dt.isValid ? dt.toFormat(fmt) : ""
}

function toISO({date, time, dateTime}: { date?: string; time?: string; dateTime?: string }): string {
  const base = dateTime ? DateTime.fromISO(dateTime) : null
  const hasBase = !!base && base.isValid
  const d = date ?? (hasBase ? base!.toFormat("yyyy-MM-dd") : undefined)
  const t = time ?? (hasBase ? base!.toFormat("HH:mm") : undefined)
  if (!d && !t) return ""
  if (d && t) return DateTime.fromFormat(`${d} ${t}`, "yyyy-MM-dd HH:mm").toISO() || ""
  if (d) return (DateTime.fromFormat(d, "yyyy-MM-dd").set({
    hour: hasBase ? base!.hour : 0,
    minute: hasBase ? base!.minute : 0,
  }).toISO() || "")
  if (t) {
    const ref = hasBase ? base! : DateTime.now()
    const [h, m] = (t || "00:00").split(":").map(Number)
    return ref.set({hour: h ?? 0, minute: m ?? 0}).toISO() || ""
  }
  return ""
}

watch([() => event.value.startTime, () => event.value.endTime, sameEndDate], () => {
  if (!sameEndDate.value) return
  const end = DateTime.fromISO(event.value.endTime)
  const time = end.isValid ? end.toFormat("HH:mm") : "00:00"
  event.value.endTime = toISO({time, dateTime: event.value.startTime})
})

watch(
  () => event.value.signUp,
  (on) => {
    if (!on) {
      event.value.signUpForm = undefined
      enableSignUpForm.value = false
    }
  },
)

watch(enableSignUpForm, (on) => {
  if (on) event.value.signUp = true
  else event.value.signUpForm = undefined
})

const bannerFile = ref<File | null>(null)
const bannerDirty = ref(false)

function getFirstFile(value: File | File[] | null | undefined): File | null {
  if (Array.isArray(value)) return value[0] ?? null
  return (value as File | null) ?? null
}

defineRule("fileSize", (value: File | File[] | null) => {
  const f = getFirstFile(value)
  if (!f) return true
  return f.size <= 2 * 1024 * 1024 || "Promo image must be ≤ 2MB"
})

async function loadBanner() {
  if (!event.value?.id || !event.value.banner) return
  try {
    const resp = await downloadEventBanner({
      path: {bannerId: event.value.banner.id!},
      throwOnError: true,
      responseType: "blob",
    })
    const blob = resp?.data as Blob
    if (!blob) return
    bannerFile.value = new File([blob], event.value.banner.file.name!, {
      type: blob.type || "application/octet-stream",
      lastModified: Date.now(),
    })
    bannerDirty.value = false
  } catch (e) {
    console.error("Failed to download event banner:", e)
  }
}

async function onBannerChange(val: File | null, handleChange: (v: File | null) => void) {
  const file = getFirstFile(val)
  const res = await formRef.value?.validateField("banner")
  if (file && !res?.valid) return

  bannerFile.value = file ?? null
  bannerDirty.value = true
  handleChange(file ?? null)
}

async function fetchCommittees() {
  const resp = await findCommittees()
  if (resp.status === 200) committees.value = (resp.data ?? []) as AdvancedCommittee[]
  else $handleNetworkError(resp)
}

onMounted(async () => {
  await Promise.all([loadBanner(), fetchCommittees()])
})

async function submit() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return
  submitting.value = true
  try {
    if (bannerDirty.value) {
      if (bannerFile.value) {
        const uploadResp = await uploadEventBanner({body: {file: bannerFile.value}})
        if (uploadResp.status === 201) {
          // If the banner is the same as before, then we can just keep the old ref.
          if (event.value.banner?.file.id !== uploadResp.data?.id) {
            event.value.banner = {file: uploadResp.data!} as EventBanner
          }
        } else if (!apply(formRef.value!, uploadResp)) {
          $handleNetworkError(uploadResp)
          submitting.value = false
          return
        }
      } else {
        event.value.banner = undefined
      }
    }

    if (event.value?.id)
      await updateEvent({path: {id: event.value.id}, body: event.value, throwOnError: true})
    else
      await createEvent({body: event.value, throwOnError: true})

    router.back()
  } catch (e: unknown) {
    if (!apply(formRef.value!, e)) $handleNetworkError(e)
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
              @update:model-value="(date: string) => handleChange(toISO({ date, dateTime: event.startTime }))"
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
              @update:model-value="(date: string) => handleChange(toISO({ date, dateTime: event.endTime }))"
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
              :model-value="safeFormatISO(value, 'HH:mm')"
              label="End time"
              prepend-icon="mdi-clock"
              type="time"
              @blur="handleBlur"
              @update:model-value="(time: string) => handleChange(toISO({ time, dateTime: event.endTime }))"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="event.committeeId"
            name="committeeId"
            rules="required"
          >
            <v-select
              :disabled="!committees.length"
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

        <!-- CHANGED: bind the field to bannerFile and defer upload -->
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="bannerFile"
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
            rules="required"
          >
            <survey-form
              v-model="event.signUpForm"
              :error-messages="errors"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-expand-transition>
        <v-alert
          v-if="(hadSignUp && !event.signUp) || (oldEnableSignUpForm && !event.signUpForm)"
          class="mt-4 mx-3"
          prominent
          type="warning"
          variant="outlined"
        >
          Woah there! Looks like you changed sign-up settings. Once you submit, any existing sign-ups <b>will be
            removed</b>!
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
