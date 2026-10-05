<script lang="ts" setup>
/* Queues one job by hand. A preset fills it in from a job already run, and nothing is queued
   until Queue the job is pressed. */
import {computed, nextTick, onMounted, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {enqueueJob, type JobPayloadField, type JobTypeDescriptor, listJobTypes} from "@/domains/jobs"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import TargetPicker from "@/components/form/fields/TargetPicker.vue"
import EventPicker from "@/components/form/fields/EventPicker.vue"
import ContributionPeriodPicker from "@/components/form/fields/ContributionPeriodPicker.vue"
import EnumPicker from "@/components/form/fields/EnumPicker.vue"
import {humanizeJobType, jobCatalogEntry} from "@/utils/jobCatalog"

defineOptions({name: "JobRunForm"})

export interface JobPreset {
  type: string
  payload: Record<string, unknown>
}

const {preset = null} = defineProps<{preset?: JobPreset | null}>()
const emit = defineEmits<{queued: [jobType: string]}>()

const descriptors = ref<JobTypeDescriptor[]>([])
const loadingTypes = ref<boolean>(false)
const selectedType = ref<string | null>(null)
// Raw text for free-text and numeric fields, the picked id or enum value for picker fields;
// buildPayload coerces them.
const fieldValues = ref<Record<string, unknown>>({})
const submitting = ref<boolean>(false)
const errorMessage = ref<string | null>(null)
const queuedType = ref<string | null>(null)

const humanize = (value: string): string => humanizeJobType(value)

const typeOptions = computed(() =>
  descriptors.value.map((descriptor) => ({
    key: descriptor.type,
    label: jobCatalogEntry(descriptor.type).title,
    terms: [descriptor.type],
  })),
)

const selectedDescriptor = computed<JobTypeDescriptor | null>(
  () => descriptors.value.find((descriptor) => descriptor.type === selectedType.value) ?? null,
)

const selectedDescription = computed<string>(() =>
  selectedType.value ? jobCatalogEntry(selectedType.value).description : "",
)

const NUMERIC_TYPES = new Set(["Long", "Int", "Integer", "Short", "Double", "Float", "BigDecimal", "BigInteger"])
const isNumeric = (field: JobPayloadField): boolean => NUMERIC_TYPES.has(field.type)
const isEnum = (field: JobPayloadField): boolean => field.kind === "ENUM"

const numberValue = (name: string): number | undefined => {
  const v = fieldValues.value[name]
  return typeof v === "number" ? v : undefined
}

const stringValue = (name: string): string | undefined => {
  const v = fieldValues.value[name]
  return typeof v === "string" ? v : undefined
}

type PickerKind = "user" | "cohort" | "event" | "contributionPeriod" | null

/**
 * Convention-based picker dispatch: a `Long`-typed field ending in
 * `userId` / `cohortId` or `targetId` / `eventId` / `contributionPeriodId` (or
 * `periodId`) gets the matching picker, so the form stays blind to payload shapes.
 */
const pickerForField = (field: JobPayloadField): PickerKind => {
  if (!isNumeric(field)) return null
  const name = field.name.toLowerCase()
  if (name === "userid" || name.endsWith("userid")) return "user"
  if (name.endsWith("cohortid") || name === "targetid") return "cohort"
  if (name === "eventid" || name.endsWith("eventid")) return "event"
  if (name.endsWith("contributionperiodid") || name === "periodid" || name.endsWith("periodid")) {
    return "contributionPeriod"
  }
  return null
}

const requiredMissing = computed<boolean>(() =>
  (selectedDescriptor.value?.payloadFields ?? []).some((field) => {
    if (!field.required) return false
    const value = fieldValues.value[field.name]
    if (value == null) return true
    if (typeof value === "string") return value.trim() === ""
    return false
  }),
)

const loadTypes = async () => {
  loadingTypes.value = true
  try {
    descriptors.value = (await listJobTypes()).slice().sort((a, b) => a.type.localeCompare(b.type))
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loadingTypes.value = false
  }
}

/** A text field holds what was typed, so a preset's number or flag goes in as its text. */
const presetValue = (field: JobPayloadField, value: unknown): unknown =>
  pickerForField(field) !== null || isEnum(field) ? value : String(value)

const applyPreset = async (next: JobPreset | null) => {
  if (!next) return
  selectedType.value = next.type
  // The type's own watcher empties the fields first; the preset fills them after it.
  await nextTick()
  const fields = descriptors.value.find((descriptor) => descriptor.type === next.type)?.payloadFields ?? []
  fieldValues.value = Object.fromEntries(
    fields.filter((field) => next.payload[field.name] != null).map((field) => [field.name, presetValue(field, next.payload[field.name])]),
  )
}

onMounted(async () => {
  await loadTypes()
  await applyPreset(preset)
})

watch(() => preset, (next) => void applyPreset(next))

watch(selectedType, () => {
  fieldValues.value = {}
  errorMessage.value = null
  queuedType.value = null
})

const buildPayload = (): Record<string, unknown> => {
  const payload: Record<string, unknown> = {}
  for (const field of selectedDescriptor.value?.payloadFields ?? []) {
    const value = fieldValues.value[field.name]
    if (value == null) continue
    if (typeof value === "string") {
      const trimmed = value.trim()
      if (trimmed === "") continue
      if (field.type === "Boolean") payload[field.name] = trimmed.toLowerCase() === "true"
      else if (isNumeric(field)) payload[field.name] = Number(trimmed)
      else payload[field.name] = trimmed
    } else {
      payload[field.name] = value
    }
  }
  return payload
}

const submit = async () => {
  if (!selectedType.value || requiredMissing.value) return
  submitting.value = true
  errorMessage.value = null
  try {
    const result = await enqueueJob(selectedType.value, buildPayload())
    if (result.ok) {
      queuedType.value = selectedType.value
      emit("queued", selectedType.value)
    } else {
      errorMessage.value = result.reason
    }
  } catch (error) {
    errorMessage.value = (error as Error)?.message ?? "The job could not be queued."
    $handleNetworkError(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div
    class="job-run"
    data-testid="job-run-form"
  >
    <div class="job-run__fields">
      <form-field
        :filled="selectedType != null"
        label="Job"
        testid="job-run-type"
        variant="inside"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :labelled-by="labelId"
            :loading="loadingTypes"
            :options="typeOptions"
            :selected-key="selectedType"
            testid-prefix="job-run-type-picker"
            @pick="selectedType = $event"
          />
        </template>
      </form-field>

      <template
        v-for="field in selectedDescriptor?.payloadFields ?? []"
        :key="field.name"
      >
        <!-- A payload field named xxxUserId, xxxCohortId, xxxEventId or xxxPeriodId takes that
             record's picker, so a new job type needs nothing here. -->
        <user-picker
          v-if="pickerForField(field) === 'user'"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="numberValue(field.name)"
          :required="field.required"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <target-picker
          v-else-if="pickerForField(field) === 'cohort'"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="numberValue(field.name)"
          :required="field.required"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <event-picker
          v-else-if="pickerForField(field) === 'event'"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="numberValue(field.name)"
          :required="field.required"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <contribution-period-picker
          v-else-if="pickerForField(field) === 'contributionPeriod'"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="numberValue(field.name)"
          :required="field.required"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <enum-picker
          v-else-if="isEnum(field)"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="stringValue(field.name)"
          :required="field.required"
          :values="field.enumValues ?? []"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <form-field
          v-else
          v-slot="slot"
          :filled="(stringValue(field.name) ?? '') !== ''"
          :label="humanize(field.name)"
          :required="field.required"
          :testid="`job-run-field-${field.name}`"
          variant="inside"
        >
          <text-input
            :control-id="slot.controlId"
            :model-value="stringValue(field.name) ?? ''"
            :type="isNumeric(field) ? 'number' : 'text'"
            @update:model-value="fieldValues[field.name] = $event"
          />
        </form-field>
      </template>
    </div>

    <p
      v-if="selectedDescription"
      class="job-run__note"
      data-testid="job-run-description"
    >
      {{ selectedDescription }}
      <template v-if="selectedDescriptor && selectedDescriptor.payloadFields.length === 0">
        This job takes no arguments.
      </template>
    </p>

    <notice-box
      v-if="errorMessage"
      testid="job-run-error"
      tone="danger"
    >
      {{ errorMessage }}
    </notice-box>

    <div class="job-run__actions">
      <cut-button
        :disabled="!selectedType || requiredMissing || submitting"
        testid="job-run-submit"
        tone="solid"
        @click="submit"
      >
        Queue the job
      </cut-button>
      <p
        v-if="queuedType"
        class="job-run__queued"
        data-testid="job-run-queued"
        role="status"
      >
        {{ jobCatalogEntry(queuedType).title }} is queued.
      </p>
    </div>
  </div>
</template>

<style scoped>
.job-run {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

/* Every field the same width, as many to a row as fit. */
.job-run__fields {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
  gap: 0.5rem;
  align-items: start;
}

.job-run__note {
  font-size: 0.88rem;
  color: var(--color-ash);
}

.job-run__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1rem;
}

.job-run__queued {
  font-size: 0.88rem;
  color: var(--color-ok);
}
</style>
