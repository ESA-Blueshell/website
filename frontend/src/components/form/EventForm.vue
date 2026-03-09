<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import {defineRule, Form} from "vee-validate"
import MarkdownField from "@/components/form/fields/MarkdownField.vue"
import SurveyForm from "@/components/form/SurveyForm.vue"
import {useStore} from "vuex"
import {apply, type FieldMap} from "@/plugins/validation.ts"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox, VFileInput, VSelect} from "vuetify/components"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {
  type CommitteeDetailResponse,
  createEvent,
  type CreateEventRequest,
  downloadEventBanner,
  type EventBannerRequest,
  findCommittees,
  findCommitteesByUserId,
  type QuestionRequest,
  type SurveyRequest,
  type UpdateEventRequest,
  updateEvent,
  uploadEventBanner,
} from "@/services/api"
import {handleSubmitError, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"
import {safeFormatISO, toISO} from "@/utils/datetime"
import type {HandleChange} from "@/types/VVField.types.ts"

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

type CommitteeOption = Pick<CommitteeDetailResponse, "id" | "name">
type EventModel = Omit<CreateEventRequest, "committeeId" | "banner" | "signUpForm"> & {
  committeeId?: number;
  id?: number;
  version?: number;
  banner?: EventBannerRequest;
  signUpForm?: SurveyRequest;
  signUpCount?: number;
  signUpDeadline?: string;
  signUpLimit?: number;
}

const event = defineModel<EventModel>({
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
    signUpDeadline: undefined,
    signUpLimit: undefined,
    banner: undefined,
    committeeId: undefined,
  }),
})
if (!event.value) {
  event.value = {
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
    signUpDeadline: undefined,
    signUpLimit: undefined,
    banner: undefined,
    committeeId: undefined,
  }
}

const store = useStore()
const isBoard = computed<boolean>(() => store.getters.isBoard)

const committees = ref<CommitteeOption[]>([])
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

const eventFieldMap: FieldMap = {
  "banner.fileId": "banner",
}

watch(
  () => event.value.signUp,
  (on) => {
    if (!on) {
      event.value.signUpForm = undefined
      enableSignUpForm.value = false
      event.value.signUpDeadline = undefined
      event.value.signUpLimit = undefined
    } else if (!event.value.signUpDeadline) {
      event.value.signUpDeadline = event.value.startTime
    }
  },
  {immediate: true},
)
watch(enableSignUpForm, (on) => {
  if (on) event.value.signUp = true
  else event.value.signUpForm = undefined
})
watch(
  () => event.value.startTime,
  (newStartTime, oldStartTime) => {
    if (!event.value.signUp) return
    if (!event.value.signUpDeadline || event.value.signUpDeadline === oldStartTime) {
      event.value.signUpDeadline = newStartTime
    }
  },
)

const bannerFile = ref<File | null>(null)
const bannerDirty = ref(false)

async function loadBanner() {
  if (!event.value?.id || !event.value.banner) return
  try {
    const resp = await downloadEventBanner({
      path: {eventId: event.value.id},
      throwOnError: true,
      responseType: "blob",
    })
    const blob = resp?.data as Blob
    if (!blob) return
    bannerFile.value = new File([blob], `event-banner-${event.value.id}`, {
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
  const resp = isBoard.value
    ? await findCommittees()
    : await findCommitteesByUserId()
  if (resp.status === 200) {
    committees.value = ((resp.data ?? []) as unknown[])
      .map((committee) => {
        const value = committee as Record<string, unknown>
        const id = typeof value.id === "number" ? value.id : null
        const name = typeof value.name === "string" ? value.name : null
        if (id == null || name == null) return null
        return {id, name}
      })
      .filter((committee): committee is CommitteeOption => committee != null)
  }
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
            if (event.value.banner?.fileId !== uploadResp.data?.id) {
              event.value.banner = {
                fileId: uploadResp.data!.id,
                version: event.value.banner?.version,
              } as EventBannerRequest
            }
          } else if (!apply(formRef.value!, uploadResp, eventFieldMap)) {
            handleSubmitError(formRef.value, uploadResp, eventFieldMap)
            setSubmitResult(false)
            return
          }
        } else {
          event.value.banner = undefined
        }
      }

      const surveyRequest: SurveyRequest | undefined = event.value.signUpForm
        ? {
          questions: event.value.signUpForm.questions.map(
            (question): QuestionRequest => ({
              idx: question.idx,
              label: question.label,
              type: question.type,
              choiceLabels: question.choiceLabels,
            }),
          ),
        }
        : undefined

      const bodyBase = {
        committeeId: event.value.committeeId!,
        title: event.value.title,
        description: event.value.description,
        location: event.value.location,
        startTime: event.value.startTime,
        endTime: event.value.endTime,
        memberPrice: event.value.memberPrice,
        publicPrice: event.value.publicPrice,
        approved: event.value.approved,
        membersOnly: event.value.membersOnly,
        signUp: event.value.signUp,
        signUpDeadline: event.value.signUp ? event.value.signUpDeadline : undefined,
        signUpLimit: event.value.signUp && event.value.signUpLimit != null ? Number(event.value.signUpLimit) : undefined,
        banner: event.value.banner
          ? {
            fileId: event.value.banner.fileId,
            version: event.value.banner.version,
          }
          : undefined,
        signUpForm: surveyRequest,
      } as CreateEventRequest

      const resp = event.value?.id
        ? await updateEvent({
          path: {id: event.value.id},
          body: {
            ...(bodyBase as UpdateEventRequest),
            version: event.value.version!,
          },
          throwOnError: true,
        })
        : await createEvent({body: bodyBase, throwOnError: true})

      event.value = resp.data!
      emit("submitted", true)
      setSubmitResult(true)
    })
  } catch (e: unknown) {
    handleSubmitError(formRef.value, e, eventFieldMap)
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
            test-id="event-form-title-field"
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
            test-id="event-form-location-field"
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
            test-id="event-form-description-field"
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
            test-id="event-form-approved-field"
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
            :component-props="{ type: 'datetime-local', 'prepend-icon': 'mdi-clock' }"
            :display="(v: string) => safeFormatISO(String(v ?? ''), `yyyy-MM-dd'T'HH:mm`)"
            :rules="event.id ? 'required' : `required|dateTimeAfter:${nowISO}`"
            :update="(v: string, handle: HandleChange<string>) => handle(toISO({ dateTime: v }))"
            label="Start time"
            name="startTime"
          />
        </v-col>
        <v-col>
          <VvField
            v-model="event.endTime"
            :component-props="{ type: 'datetime-local', 'prepend-icon': 'mdi-clock' }"
            :display="(v: string) => safeFormatISO(String(v ?? ''), `yyyy-MM-dd'T'HH:mm`)"
            :update="(v: string, handle: HandleChange<string>) => handle(toISO({ dateTime: v }))"
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
            test-id="event-form-committee-field"
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
            test-id="event-form-banner-field"
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

      <template v-if="event.signUp">
        <v-row>
          <v-col>
            <VvField
              v-model="event.signUpDeadline"
              :component-props="{ type: 'datetime-local', 'prepend-icon': 'mdi-clock' }"
              :display="(v: string) => safeFormatISO(String(v ?? ''), `yyyy-MM-dd'T'HH:mm`)"
              :rules="`required|dateTimeNotAfter:@endTime`"
              :update="(v: string, handle: HandleChange<string>) => handle(toISO({ dateTime: v }))"
              label="Sign-up deadline"
              name="signUpDeadline"
            />
          </v-col>
          <v-col>
            <VvField
              v-model="event.signUpLimit"
              :component-props="{ type: 'number', min: 1, clearable: true, 'prepend-icon': 'mdi-account-multiple' }"
              :update="(raw: string, handle: HandleChange<string>) => handle(raw === '' ? '' : raw)"
              label="Maximum sign-ups"
              name="signUpLimit"
              rules="minValue:1"
            />
          </v-col>
        </v-row>
      </template>

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
          data-testid="event-form-submit-btn"
          :data-submit-mode="event.id ? 'update' : 'create'"
          text="Submit event"
          @click="save"
        />
      </v-col>
    </v-row>
  </Form>
</template>
