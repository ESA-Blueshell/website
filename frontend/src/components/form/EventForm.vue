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
            name="title"
            label="Event name"
            rules="required"
          />
        </v-col>

        <v-col
          cols="12"
          lg="4"
        >
          <VvField
            v-model="event.location"
            name="location"
            label="Location"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row class="mb-8">
        <v-col>
          <VvField
            v-model="event.description"
            :component="MarkdownField"
            name="description"
            label="Description"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.memberPrice"
            name="memberPrice"
            label="Price for members"
            rules="minValue:0|maxValue:99.99"
            :component-props="{ 'prepend-icon': 'mdi-currency-eur', type: 'number', step: '0.01', inputmode: 'decimal' }"
            :update="(raw: string, handle) => handle(raw === '' ? '' : Number(raw))"
          />
        </v-col>

        <v-col>
          <VvField
            v-model="event.publicPrice"
            name="publicPrice"
            label="Price for non-members"
            rules="minValue:0|maxValue:99.99"
            :component-props="{ 'prepend-icon': 'mdi-currency-eur', type: 'number', step: '0.01', inputmode: 'decimal' }"
            :update="(raw: string, handle) => handle(raw === '' ? '' : Number(raw))"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="sameEndDate"
            :component="VCheckbox"
            name="sameEndDate"
            :component-props="{ label: 'Same start and end date' }"
          />
        </v-col>

        <v-col>
          <VvField
            v-model="event.membersOnly"
            :component="VCheckbox"
            name="membersOnly"
            :component-props="{ label: 'Members only' }"
          />
        </v-col>

        <v-col>
          <VvField
            v-if="isBoard"
            v-model="event.approved"
            :component="VCheckbox"
            name="approved"
            :component-props="{ label: 'Approved' }"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.startTime"
            name="startDate"
            label="Start date"
            rules="required"
            :component-props="{ type: 'date', 'prepend-icon': 'mdi-calendar' }"
            :display="(v) => safeFormatISO(String(v ?? ''), 'yyyy-MM-dd')"
            :update="(date, handle) => handle(toISO({ date: String(date), dateTime: event.startTime }))"
          />
        </v-col>

        <v-col>
          <VvField
            v-model="event.startTime"
            name="startTime"
            label="Start time"
            :rules="event.id ? 'required' : `required|dateTimeAfter:${nowISO}`"
            :component-props="{ type: 'time', 'prepend-icon': 'mdi-clock' }"
            :display="(v) => safeFormatISO(String(v ?? ''), 'HH:mm')"
            :update="(time, handle) => handle(toISO({ time: String(time), dateTime: event.startTime }))"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.endTime"
            name="endDate"
            label="End date"
            rules="required|dateTimeAfter:@startDate"
            :disabled="sameEndDate"
            :component-props="{ type: 'date', 'prepend-icon': 'mdi-calendar' }"
            :display="(v) => safeFormatISO(String(v ?? ''), 'yyyy-MM-dd')"
            :update="(date, handle) => handle(toISO({ date: String(date), dateTime: event.endTime }))"
          />
        </v-col>

        <v-col>
          <VvField
            v-model="event.endTime"
            name="endTime"
            label="End time"
            rules="required|dateTimeAfter:@startTime"
            :component-props="{ type: 'time', 'prepend-icon': 'mdi-clock' }"
            :display="(v) => safeFormatISO(String(v ?? ''), 'HH:mm')"
            :update="(time, handle) => handle(toISO({ time: String(time), dateTime: event.endTime }))"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.committeeId"
            name="committeeId"
            label="Representative committee"
            rules="required"
            :component="VSelect"
            :component-props="{
              items: committees,
              'item-title': 'name',
              'item-value': 'id',
              'prepend-icon': 'mdi-account-group',
              disabled: !committees.length
            }"
          />
        </v-col>

        <v-col>
          <VvField
            v-model="bannerFile"
            name="banner"
            label="Promo image (Max 2MB)"
            rules="fileSize"
            :component="VFileInput"
            :component-props="{
              accept: 'image/png, image/jpeg, image/jpg, image/webp, image/gif',
              clearable: true,
              'show-size': true
            }"
            :update="(file, handle) => onBannerChange(file as File | null, handle)"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <VvField
            v-model="event.signUp"
            :component="VCheckbox"
            name="signUp"
            :component-props="{ label: 'Enable sign-up', 'hide-details': true }"
          />
        </v-col>
        <v-col>
          <VvField
            v-model="enableSignUpForm"
            :component="VCheckbox"
            name="enableSignUpForm"
            :component-props="{ label: 'Enable sign-up form', 'hide-details': true }"
          />
        </v-col>
      </v-row>

      <v-row v-if="enableSignUpForm">
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
        <v-btn
          :disabled="hasStarted"
          :loading="isSaving"
          block
          class="mt-8 mx-auto"
          color="primary"
          :prepend-icon="event.id ? 'mdi-content-save-edit' : 'mdi-content-save'"
          @click="save"
        >
          Submit event
        </v-btn>
      </v-col>
    </v-row>
  </Form>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import {defineRule, Form, type FormContext} from "vee-validate"
import MarkdownField from "@/components/form/fields/MarkdownField.vue"
import SurveyForm from "@/components/form/SurveyForm.vue"
import {useStore} from "vuex"
import router from "@/plugins/router.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {apply} from "@/plugins/validation.ts"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox, VFileInput, VSelect} from "vuetify/components"
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

const props = defineProps({
  initialEvent: {type: Object as () => Event, default: () => null},
  hasPromo: {type: Boolean, default: false},
})

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
  (e: "update:modelValue", value: Event): void
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
    committeeId: 0,
  }),
})

const store = useStore()
const isBoard = computed<boolean>(() => store.getters.isBoard)

const committees = ref<AdvancedCommittee[]>([])
const sameEndDate = ref(true)
const formRef = ref<FormContext>()
const isSaving = ref(false)
const nowISO = DateTime.now().toISO()
const hasStarted = computed(() => !!event.value.id && DateTime.fromISO(event.value.startTime) < DateTime.now())

const hadSignUp = ref<boolean>(!!event.value.signUp)
const oldEnableSignUpForm = ref<boolean>(!!event.value.signUpForm)
const enableSignUpForm = ref<boolean>(!!props.initialEvent?.signUpForm)

const initialJson = ref(JSON.stringify(event.value))
const isDirty = computed(() => JSON.stringify(event.value) !== initialJson.value)

function safeFormatISO(iso: string, fmt: string) {
  const dt = DateTime.fromISO(iso || "")
  return dt.isValid ? dt.toFormat(fmt) : ""
}

/** Convert separate date/time edits to full ISO strings */
function toISO(args: { date?: string; time?: string; dateTime?: string }): string {
  const {date, time, dateTime} = args
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

// Keep end-date equal to start date while toggle is on
watch([() => event.value.startTime, () => event.value.endTime, sameEndDate], () => {
  if (!sameEndDate.value) return
  const end = DateTime.fromISO(event.value.endTime)
  const time = end.isValid ? end.toFormat("HH:mm") : "00:00"
  event.value.endTime = toISO({time, dateTime: event.value.startTime})
})

watch(() => event.value.signUp, (on) => {
  if (!on) {
    event.value.signUpForm = undefined
    enableSignUpForm.value = false
  }
})
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
  } catch {
    /* optional */
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
  const resp = await findCommitteesForCurrentUser()
  if (resp.status === 200) committees.value = (resp.data ?? []) as AdvancedCommittee[]
  else $handleNetworkError(resp)
}

onMounted(async () => {
  await Promise.all([loadBanner(), fetchCommittees()])
})

const validate = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const save = async () => {
  if (!(await validate())) {
    emit("submitted", false)
    return
  }
  isSaving.value = true
  try {
    if (bannerDirty.value) {
      if (bannerFile.value) {
        const uploadResp = await uploadEventBanner({body: {file: bannerFile.value}})
        if (uploadResp.status === 201) {
          if (event.value.banner?.file.id !== uploadResp.data?.id) {
            event.value.banner = {file: uploadResp.data!} as EventBanner
          }
        } else if (!apply(formRef.value!, uploadResp)) {
          $handleNetworkError(uploadResp)
          isSaving.value = false
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
    emit("update:modelValue", event.value)
    emit("submitted", true)
    router.back()
  } catch (e: unknown) {
    if (!apply(formRef.value!, e)) $handleNetworkError(e)
    emit("submitted", false)
  } finally {
    isSaving.value = false
  }
}

defineExpose({validate, save})
</script>

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
