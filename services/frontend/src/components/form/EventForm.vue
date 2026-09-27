<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {DateTime} from "luxon"
import {Form} from "vee-validate"
import SurveyForm from "@/components/form/SurveyForm.vue"
import {useStore} from "vuex"
import VvField from "@/components/form/fields/VvField.vue"
import PingedRolePicker from "@/domains/discord/island/PingedRolePicker.vue"
import EventGamesPicker from "@/domains/games/island/EventGamesPicker.vue"
import CommitteePicker from "@/components/form/fields/CommitteePicker.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormFields from "@/components/island/FormFields.vue"
import FormSection from "@/components/island/FormSection.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import type {Picture, PictureStore} from "@/components/island/pictures"
import NoticeBox from "@/components/island/NoticeBox.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"
import EventPreview from "@/domains/events/island/EventPreview.vue"
import {
  type CreateEventRequest,
  type EventBannerRequest,
  type QuestionRequest,
  readEventBanner,
  saveEvent,
  saveEventBanner,
  saveNewEvent,
  type SurveyRequest,
  type UpdateEventRequest,
} from "@/domains/events"
import {
  type CommitteeDetailResponse,
  listCommittees,
  listMyCommittees,
} from "@/domains/committees"
import {handleSubmitError, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"
import {safeFormatISO, toISO} from "@/utils/datetime"
import type {HandleChange} from "@/types/VVField.types.ts"

const props = defineProps<{
  modelValue?: EventModel
  /** The committee a new event starts on, where the page it was added from belongs to one. */
  committeeId?: number
}>()

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
  (e: "cancel"): void
  (e: "update:modelValue", val: EventModel): void
}>()

type CommitteeOption = Pick<CommitteeDetailResponse, "id" | "name">
type EventModel = Omit<CreateEventRequest, "committeeId" | "banner" | "signUpForm"> & {
  committeeId?: number | null;
  id?: number;
  version?: number;
  banner?: EventBannerRequest | null;
  signUpForm?: SurveyRequest | null;
  signUpCount?: number;
  signUpDeadline?: string | null;
  signUpLimit?: number | null;
}

function defaultEvent(): EventModel {
  return {
    id: undefined,
    title: "",
    location: "",
    description: "",
    startTime: DateTime.now().plus({days: 1}).toISO()!,
    endTime: DateTime.now().plus({days: 1, hours: 3}).toISO()!,
    memberPrice: 0,
    publicPrice: 0,
    approved: false,
    membersOnly: false,
    signUp: false,
    signUpDeadline: undefined,
    signUpLimit: undefined,
    banner: undefined,
    committeeId: props.committeeId,
    pingedRoles: [],
    gameCodes: [],
  }
}

// Use a local ref (always deeply reactive) so that property mutations trigger watchers
// and template updates in both create mode and edit mode.
const event = ref<EventModel>(props.modelValue ? {...props.modelValue} : defaultEvent())

const store = useStore()
const isBoard = computed<boolean>(() => store.getters.isBoard)

const committees = ref<CommitteeOption[]>([])
const {formRef, validate} = useVeeForm()
const {isSaving, withSaving} = useSaving()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

/* The save button says how the last press went for a moment, then what it does again. */
const saveSaid = computed<string>(() => {
  if (isSaving.value) return "Saving"
  if (showSubmitStatus.value) return submitState.value === "success" ? "Saved" : "Check the form"
  return event.value.id ? "Save changes" : "Add event"
})

/* Keeping or dropping the sign-ups an edited form no longer fits, said as the radio keys. */
const disposition = computed<string>({
  get: () => (removeExistingSignUps.value ? "delete" : "retain"),
  set: (key) => {
    removeExistingSignUps.value = key === "delete"
  },
})
const DISPOSITIONS = [
  {key: "retain", label: "Retain event sign-ups"},
  {key: "delete", label: "Delete event sign-ups"},
]

/* The board approves; anybody else saving is told what that does to the event. */
const approvalSaid = computed<string>(() => (event.value.id
  ? "The event will be hidden until the board re-approves it"
  : "The event will be hidden until the board approves it"))
const nowISO = DateTime.now().toISO()

const hadSignUp = ref<boolean>(!!event.value.signUp)
const enableSignUpForm = ref<boolean>(!!event.value.signUpForm)
const removeExistingSignUps = ref<boolean>(false)

const initialEvent = ref(JSON.stringify(event.value))
const eventIsDirty = computed(() => JSON.stringify(event.value) !== initialEvent.value)

const initialSignUpForm = ref(JSON.stringify(event.value.signUpForm))
const signUpFormIsDirty = computed(() => JSON.stringify(event.value.signUpForm) != initialSignUpForm.value)

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
    if (event.value.signUp) {
      if (!event.value.signUpDeadline || event.value.signUpDeadline === oldStartTime) {
        event.value.signUpDeadline = newStartTime
      }
    }
    const oldDate = oldStartTime?.slice(0, 10)
    const newDate = newStartTime?.slice(0, 10)
    if (oldDate && newDate && oldDate !== newDate && event.value.endTime) {
      event.value.endTime = newDate + event.value.endTime.slice(10)
    }
  },
)

const bannerFile = ref<File | null>(null)
const bannerDirty = ref(false)

async function loadBanner() {
  if (!event.value?.id || !event.value.banner) return
  try {
    const blob = await readEventBanner(event.value.id)
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

/* The poster is kept here until the event is saved, since a new event has no record to store it on. */
const posterShown = ref<Picture | null>(null)
let posterChosen: File | null = null
const POSTER_MAX_BYTES = 10 * 1024 * 1024

const showPoster = (file: File | null) => {
  if (posterShown.value) URL.revokeObjectURL(posterShown.value.url)
  posterShown.value = file ? {path: "", url: URL.createObjectURL(file), renditions: []} : null
}

const holdPoster: PictureStore = async (file) => {
  if (file.size > POSTER_MAX_BYTES) return {ok: false, reason: "A poster is at most 10 MB."}
  posterChosen = file
  return {ok: true, picture: {path: "", url: "", renditions: []}}
}

function onPoster(picture: Picture | null) {
  bannerFile.value = picture ? posterChosen : null
  bannerDirty.value = true
}

watch(bannerFile, showPoster)
onBeforeUnmount(() => showPoster(null))

async function fetchCommittees() {
  try {
    const read = isBoard.value ? await listCommittees() : await listMyCommittees()
    committees.value = (read as unknown[])
      .map((committee) => {
        const value = committee as Record<string, unknown>
        const id = typeof value.id === "number" ? value.id : null
        const name = typeof value.name === "string" ? value.name : null
        if (id == null || name == null) return null
        // An archived committee leaves the picker, but stays on an event it already runs.
        if (value.archived === true && id !== event.value.committeeId) return null
        return {id, name}
      })
      .filter((committee): committee is CommitteeOption => committee != null)
  } catch (e: unknown) {
    handleSubmitError(formRef.value, e)
  }
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
          const stored = await saveEventBanner(bannerFile.value)
          if (event.value.banner?.fileId !== stored.id) {
            event.value.banner = {
              fileId: stored.id,
              version: event.value.banner?.version,
            } as EventBannerRequest
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
              required: question.required === true,
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
        signUpLimit: event.value.signUp && event.value.signUpLimit ? Number(event.value.signUpLimit) : undefined,
        banner: event.value.banner
          ? {
            fileId: event.value.banner.fileId,
            version: event.value.banner.version,
          }
          : undefined,
        signUpForm: surveyRequest,
        pingedRoles: event.value.pingedRoles ?? [],
        gameCodes: event.value.gameCodes ?? [],
      } as CreateEventRequest

      const saved = event.value?.id
        ? await saveEvent(event.value.id, {
          ...(bodyBase as UpdateEventRequest),
          removeExistingSignUps: removeExistingSignUps.value,
          version: event.value.version!,
        })
        : await saveNewEvent(bodyBase)

      event.value = {...saved, description: saved.description ?? ""}
      emit("update:modelValue", event.value)
      emit("submitted", true)
      setSubmitResult(true)
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
    class="event-form"
  >
    <div class="event-form__grid">
      <div class="event-form__sections">
        <form-section title="The event">
          <form-fields>
            <div class="form-span">
              <image-picker
                label="Poster"
                may-be-animated
                :picture="posterShown"
                say="Choose a poster"
                shape="poster"
                :store="holdPoster"
                testid="event-form-banner-field"
                @update:picture="onPoster"
              />
            </div>
            <VvField
              v-model="event.title"
              label="Event name*"
              name="title"
              rules="required"
              test-id="event-form-title-field"
            />
            <VvField
              v-model="event.location"
              label="Location*"
              name="location"
              rules="required"
              test-id="event-form-location-field"
            />
            <VvField
              v-model="event.startTime"
              :component-props="{type: 'datetime-local'}"
              :display="(v: string) => safeFormatISO(String(v ?? ''), `yyyy-MM-dd'T'HH:mm`)"
              label="Starts*"
              name="startTime"
              :rules="event.id ? 'required' : `required|dateTimeAfter:${nowISO}`"
              :update="(v: string, handle: HandleChange<string>) => handle(toISO({dateTime: v}))"
            />
            <VvField
              v-model="event.endTime"
              :component-props="{type: 'datetime-local'}"
              :display="(v: string) => safeFormatISO(String(v ?? ''), `yyyy-MM-dd'T'HH:mm`)"
              label="Ends*"
              name="endTime"
              rules="required|dateTimeAfter:@startTime"
              :update="(v: string, handle: HandleChange<string>) => handle(toISO({dateTime: v}))"
            />
            <div class="form-span">
              <VvField
                v-model="event.committeeId"
                :component="CommitteePicker"
                :component-props="{committees, required: true}"
                label="Representative committee"
                name="committeeId"
                rules="required"
                test-id="event-form-committee-field"
              />
            </div>
            <div class="form-span">
              <event-games-picker
                v-model="event.gameCodes"
                testid="event-form-games"
              />
            </div>
            <div class="form-span">
              <VvField
                v-model="event.description"
                :component-props="{kind: 'markdown'}"
                label="Description*"
                name="description"
                rules="required"
                test-id="event-form-description-field"
              />
            </div>
          </form-fields>
        </form-section>

        <form-section title="Price and access">
          <form-fields>
            <VvField
              v-model="event.memberPrice"
              :component-props="{kind: 'money'}"
              :display="(v: unknown) => (v == null ? '' : String(v))"
              label="Price for members"
              name="memberPrice"
              rules="minValue:0"
              :update="(raw: string, handle: HandleChange<string>) => handle(raw)"
            />
            <VvField
              v-model="event.publicPrice"
              :component-props="{kind: 'money'}"
              :display="(v: unknown) => (v == null ? '' : String(v))"
              label="Price for non-members"
              name="publicPrice"
              rules="minValue:0"
              :update="(raw: string, handle: HandleChange<string>) => handle(raw)"
            />
            <div class="form-span">
              <VvField
                v-model="event.membersOnly"
                :component="CheckBox"
                label="Members only"
                name="membersOnly"
              />
            </div>
          </form-fields>
        </form-section>

        <form-section title="Discord">
          <pinged-role-picker
            v-model="event.pingedRoles"
            testid="event-form-pinged-roles"
          />
        </form-section>

        <form-section title="Sign-ups">
          <div class="event-form__checks">
            <VvField
              v-model="event.signUp"
              :component="CheckBox"
              label="Take sign-ups"
              name="signUp"
              test-id="event-form-signup-field"
            />
            <VvField
              v-model="enableSignUpForm"
              :component="CheckBox"
              label="Add a sign-up form"
              name="enableSignUpForm"
            />
          </div>
          <form-fields v-if="event.signUp">
            <VvField
              v-model="event.signUpDeadline"
              :component-props="{type: 'datetime-local'}"
              :display="(v: string) => safeFormatISO(String(v ?? ''), `yyyy-MM-dd'T'HH:mm`)"
              label="Sign-ups close*"
              name="signUpDeadline"
              :rules="`required|dateTimeNotAfter:@endTime`"
              test-id="event-form-signup-deadline-field"
              :update="(v: string, handle: HandleChange<string>) => handle(toISO({dateTime: v}))"
            />
            <VvField
              v-model="event.signUpLimit"
              :component-props="{kind: 'count'}"
              :display="(v: unknown) => (v == null ? '' : String(v))"
              label="Sign-up limit"
              name="signUpLimit"
              rules="minValue:1"
              test-id="event-form-signup-limit-field"
              :update="(raw: string, handle: HandleChange<string>) => handle(raw)"
            />
          </form-fields>
          <VvField
            v-if="enableSignUpForm"
            v-model="event.signUpForm"
            :component="SurveyForm"
            name="signUpForm"
            rules="required"
          />
          <notice-box
            v-if="event.id && (event.signUpCount ?? 0) > 0 && (signUpFormIsDirty || (hadSignUp && !event.signUp))"
            :title="removeExistingSignUps ? 'Existing sign-ups will be deleted' : 'Existing sign-ups will be retained'"
            :tone="removeExistingSignUps ? 'warning' : 'info'"
          >
            <p class="event-form__notice-line">
              <template v-if="removeExistingSignUps">
                All {{ event.signUpCount }} existing sign-ups and their answers are removed on save.
              </template>
              <template v-else>
                Answers to new or changed questions stay blank until each person updates their sign-up.
              </template>
            </p>
            <radio-group
              v-model="disposition"
              name="signup-disposition"
              :options="DISPOSITIONS"
              testid="event-form-signup-disposition"
            />
          </notice-box>
        </form-section>
      </div>

      <event-preview
        class="event-form__preview"
        :location="event.location"
        :poster="bannerFile"
        :start-time="event.startTime"
        :title="event.title"
      />
    </div>

    <div class="event-form__save">
      <VvField
        v-if="isBoard"
        v-model="event.approved"
        :component="CheckBox"
        label="Approved"
        name="approved"
        test-id="event-form-approved-field"
      />
      <p
        v-else-if="eventIsDirty || !event.id"
        class="event-form__save-note"
        data-testid="event-form-approval-note"
      >
        {{ approvalSaid }}
      </p>
      <div class="event-form__save-actions">
        <cut-button
          testid="event-form-cancel-btn"
          tone="quiet"
          @click="emit('cancel')"
        >
          Cancel
        </cut-button>
        <cut-button
          :data-submit-mode="event.id ? 'update' : 'create'"
          :disabled="isSaving"
          testid="event-form-submit-btn"
          tone="solid"
          @click="save"
        >
          {{ saveSaid }}
        </cut-button>
      </div>
    </div>
  </Form>
</template>

<style scoped>
.event-form__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 22rem;
  gap: 3rem;
  align-items: start;
}

.event-form__checks {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 2rem;
}

.event-form__notice-line {
  margin-bottom: 0.75rem;
}

/* Raised and narrower than the page, so it reads as the form's own rather than as the footer. */
.event-form__save {
  position: sticky;
  bottom: 1rem;
  z-index: 3;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.9rem 2rem;
  max-width: calc(100% - 25rem);
  margin-top: 1.5rem;
  padding: 1rem 1.25rem;
  background-color: var(--color-surface);
  border-top: 3px solid var(--color-brand);
  box-shadow: 0 14px 34px rgb(0 0 0 / 30%);
}

.event-form__save-note {
  font-size: 0.88rem;
  color: var(--color-ash);
}

.event-form__save-actions {
  display: flex;
  gap: 0.5rem;
  margin-left: auto;
}

@media (max-width: 1023px) {
  .event-form__grid {
    grid-template-columns: 1fr;
  }

  .event-form__preview {
    display: none;
  }

  .event-form__save {
    max-width: none;
  }
}

@media (max-width: 767px) {
  .event-form__save {
    padding: 0.9rem 1rem;
  }
}
</style>
