<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import {defineRule, Form} from "vee-validate"
import MarkdownField from "@/components/form/fields/MarkdownField.vue"
import SurveyForm from "@/components/form/SurveyForm.vue"
import {useStore} from "vuex"
import router from "@/plugins/router.ts"
import {apply} from "@/plugins/validation.ts"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox, VFileInput, VSelect} from "vuetify/components"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {
  type AdvancedCommittee,
  createEvent,
  downloadEventBanner,
  type Event,
  type EventBanner,
  findCommitteesForCurrentUser,
  updateEvent,
  uploadEventBanner,
} from "@/services/api"
import {handleSubmitError, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"
import {safeFormatISO, toISO} from "@/utils/datetime"
import type {DisplayFn, HandleChange} from "@/types/VVField.types.ts"

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

const event = defineModel<Event>({
  default: () => ({
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
    committeeId: undefined,
  }),
})

const store = useStore()
const isBoard = computed<boolean>(() => store.getters.isBoard)

const committees = ref<AdvancedCommittee[]>([])
const sameEndDate = ref(true)
const {formRef, validate} = useVeeForm()
const {isSaving, withSaving} = useSaving()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const nowISO = DateTime.now().toISO()
const hasStarted = computed(() => !!event.value.id && DateTime.fromISO(event.value.startTime) < DateTime.now())

const hadSignUp = ref<boolean>(!!event.value.signUp)
const enableSignUpForm = ref<boolean>(!!event.value.signUpForm)

const initialEvent = ref(JSON.stringify(event.value))
const eventIsDirty = computed(() => JSON.stringify(event.value) !== initialEvent.value)

const initialSignUpForm = ref(JSON.stringify(event.value.signUpForm))
const signUpFormIsDirty = computed(() => JSON.stringify(event.value.signUpForm) != initialSignUpForm.value)

defineRule("fileSize", (value: File | File[] | null) => {
  const f = Array.isArray(value) ? value[0] ?? null : (value as File | null)
  if (!f) return true
  return f.size <= 2 * 1024 * 1024 || "Promo image must be ≤ 2MB"
})

const setEndDate = function (date: string) {
  if (!sameEndDate.value) return
  const time = DateTime.fromISO(event.value.endTime).toFormat("HH:mm")
  event.value.endTime = toISO({time, date})
}

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
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
  } catch (e: unknown) {
    // Ignore error
  }
}

async function onBannerChange(val: File | null, handleChange: (v: File | null) => void) {
  const file = Array.isArray(val) ? val[0] ?? null : val
  const res = await formRef.value?.validateField("banner")
  if (file && !res?.valid) return
  bannerFile.value = file ?? null
  bannerDirty.value = true
  handleChange(file ?? null)
}

async function fetchCommittees() {
  const resp = await findCommitteesForCurrentUser()
  if (resp.status === 200) committees.value = (resp.data ?? []) as AdvancedCommittee[]
  else handleSubmitError(formRef.value, resp)
}

onMounted(async () => {
  await Promise.all([loadBanner(), fetchCommittees()])
})

const save = async () => {
  if (!(await validate())) {
    emit("submitted", false)
    setSubmitResult(false)
    return
  }

  try {
    await withSaving(async () => {
      if (bannerDirty.value) {
        if (bannerFile.value) {
          const uploadResp = await uploadEventBanner({body: {file: bannerFile.value}})
          if (uploadResp.status === 201) {
            if (event.value.banner?.file.id !== uploadResp.data?.id) {
              event.value.banner = {file: uploadResp.data!} as EventBanner
            }
          } else if (!apply(formRef.value!, uploadResp)) {
            handleSubmitError(formRef.value, uploadResp)
            setSubmitResult(false)
            return
          }
        } else {
          event.value.banner = undefined
        }
      }

      const resp = event.value?.id
        ? await updateEvent({path: {id: event.value.id!}, body: event.value, throwOnError: true})
        : await createEvent({body: event.value, throwOnError: true})

      event.value = resp.data!
      emit("submitted", true)
      setSubmitResult(true)
      router.back()
    })
  } catch (e: unknown) {
    handleSubmitError(formRef.value, e)
    emit("submitted", false)
    setSubmitResult(false)
  }
}

defineExpose({validate, save})
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
          <VvField
            v-model="event.title"
            label="Event name"
            name="title"
            rules="required"
          />
        </v-col>
        <v-col
          cols="12"
          lg="4"
        >
          <VvField
            v-model="event.location"
            label="Location"
            name="location"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row class="mb-8">
        <v-col>
          <VvField
            v-model="event.description"
            :component="MarkdownField"
            label="Description"
            name="description"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.memberPrice"
            :component-props="{ 'prepend-icon': 'mdi-currency-eur', type: 'number', step: '0.01', inputmode: 'decimal' }"
            :update="(raw: string, handle: HandleChange<string>) => handle(raw === '' ? '' : raw)"
            label="Price for members"
            name="memberPrice"
            rules="minValue:0"
          />
        </v-col>
        <v-col>
          <VvField
            v-model="event.publicPrice"
            :component-props="{ 'prepend-icon': 'mdi-currency-eur', type: 'number', step: '0.01', inputmode: 'decimal' }"
            :update="(raw: string, handle: HandleChange<string>) => handle(raw === '' ? '' : raw)"
            label="Price for non-members"
            name="publicPrice"
            rules="minValue:0"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="sameEndDate"
            :component="VCheckbox"
            :component-props="{ label: 'Same start and end date' }"
            name="sameEndDate"
          />
        </v-col>
        <v-col>
          <VvField
            v-model="event.membersOnly"
            :component="VCheckbox"
            :component-props="{ label: 'Members only' }"
            name="membersOnly"
          />
        </v-col>
        <v-col>
          <VvField
            v-if="isBoard"
            v-model="event.approved"
            :component="VCheckbox"
            :component-props="{ label: 'Approved' }"
            name="approved"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.startTime"
            :component-props="{ type: 'date', 'prepend-icon': 'mdi-calendar' }"
            :display="(v: string) => safeFormatISO(String(v ?? ''), 'yyyy-MM-dd')"
            :update="(date: string, handle: HandleChange<string>) => {
              handle(toISO({ date, dateTime: event.startTime }))
              setEndDate(date)
            }"
            label="Start date"
            name="startDate"
            rules="required"
          />
        </v-col>
        <v-col>
          <VvField
            v-model="event.startTime"
            :component-props="{ type: 'time', 'prepend-icon': 'mdi-clock' }"
            :display="(v: DisplayFn<string>) => safeFormatISO(String(v ?? ''), 'HH:mm')"
            :rules="event.id ? 'required' : `required|dateTimeAfter:${nowISO}`"
            :update="(time: string, handle: HandleChange<string>) => handle(toISO({ time: String(time), dateTime: event.startTime }))"
            label="Start time"
            name="startTime"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.endTime"
            :component-props="{ type: 'date', 'prepend-icon': 'mdi-calendar' }"
            :disabled="sameEndDate"
            :display="(v: string) => safeFormatISO(String(v ?? ''), 'yyyy-MM-dd')"
            :update="(date: string, handle: HandleChange<string>) => handle(toISO({ date: String(date), dateTime: event.endTime }))"
            label="End date"
            name="endDate"
            rules="required|dateTimeAfter:@startDate"
          />
        </v-col>
        <v-col>
          <VvField
            v-model="event.endTime"
            :component-props="{ type: 'time', 'prepend-icon': 'mdi-clock' }"
            :display="(v: string) => safeFormatISO(String(v ?? ''), 'HH:mm')"
            :update="(time: string, handle: HandleChange<string>) => handle(toISO({ time: String(time), dateTime: event.endTime }))"
            label="End time"
            name="endTime"
            rules="required|dateTimeAfter:@startTime"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.committeeId"
            :component="VSelect"
            :component-props="{
              items: committees,
              'item-title': 'name',
              'item-value': 'id',
              'prepend-icon': 'mdi-account-group',
              disabled: !committees.length
            }"
            label="Representative committee"
            name="committeeId"
            rules="required"
          />
        </v-col>

        <v-col>
          <VvField
            v-model="bannerFile"
            :component="VFileInput"
            :component-props="{
              accept: 'image/png, image/jpeg, image/jpg, image/webp, image/gif',
              clearable: true,
              'show-size': true
            }"
            :update="(file: File, handle: HandleChange<string>) => onBannerChange(file as File | null, handle)"
            label="Promo image (Max 2MB)"
            name="banner"
            rules="fileSize"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.signUp"
            :component="VCheckbox"
            :component-props="{ label: 'Enable sign-up', 'hide-details': true }"
            name="signUp"
          />
        </v-col>
        <v-col>
          <VvField
            v-model="enableSignUpForm"
            :component="VCheckbox"
            :component-props="{ label: 'Enable sign-up form', 'hide-details': true }"
            name="enableSignUpForm"
          />
        </v-col>
      </v-row>

      <v-row
        v-if="enableSignUpForm"
      >
        <v-col>
          <VvField
            v-model="event.signUpForm"
            :component="SurveyForm"
            name="signUpForm"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-expand-transition>
        <v-alert
          v-if="(hadSignUp && !event.signUp) || signUpFormIsDirty"
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
        v-if="eventIsDirty && !isBoard"
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
        v-if="event.id && hasStarted"
        prominent
        type="error"
        variant="outlined"
      >
        It is not allowed to make changes to events which have already started.
      </v-alert>
    </v-expand-transition>

    <v-row>
      <v-col cols="12">
        <submit-button
          :block="true"
          :disabled="hasStarted || isSaving"
          :icon="event.id ? 'mdi-content-save-edit' : 'mdi-content-save'"
          :loading="isSaving"
          :show-submit-status="showSubmitStatus"
          :submit-state="submitState"
          class="mt-8 mx-auto"
          color="primary"
          text="Submit event"
          @click="save"
        />
      </v-col>
    </v-row>
  </Form>
</template>
