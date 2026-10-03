<script lang="ts" setup>
/* Queues one job by hand. A preset fills it in from a job already run, and nothing is queued
   until Queue the job is pressed. */
import {computed, nextTick, onMounted, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {enqueueJob, type JobPayloadField, type JobTypeDescriptor, listJobTypes} from "@/domains/jobs"
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
    title: jobCatalogEntry(descriptor.type).title,
    value: descriptor.type,
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
    <v-select
      v-model="selectedType"
      :items="typeOptions"
      :loading="loadingTypes"
      data-testid="job-run-type"
      item-title="title"
      item-value="value"
      label="Job type"
    />

    <!-- Mounted always, so choosing a job type does not shift the payload form. -->
    <p
      v-show="selectedDescription"
      class="text-caption text-medium-emphasis mt-n2 mb-3"
      data-testid="job-run-description"
    >
      {{ selectedDescription }}
    </p>

    <template v-if="selectedDescriptor">
      <p
        v-if="selectedDescriptor.payloadFields.length === 0"
        class="text-medium-emphasis mb-0"
      >
        This job takes no arguments.
      </p>
      <template
        v-for="field in selectedDescriptor.payloadFields"
        :key="field.name"
      >
        <!-- Reusable id pickers; the form itself stays generic
                 and any new payload field that follows the naming convention
                 (xxxUserId / xxxCohortId / xxxEventId / xxxPeriodId) picks
                 up the same control automatically. -->
        <UserPicker
          v-if="pickerForField(field) === 'user'"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="numberValue(field.name)"
          :required="field.required"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <TargetPicker
          v-else-if="pickerForField(field) === 'cohort'"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="numberValue(field.name)"
          :required="field.required"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <EventPicker
          v-else-if="pickerForField(field) === 'event'"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="numberValue(field.name)"
          :required="field.required"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <ContributionPeriodPicker
          v-else-if="pickerForField(field) === 'contributionPeriod'"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="numberValue(field.name)"
          :required="field.required"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <EnumPicker
          v-else-if="isEnum(field)"
          :data-testid="`job-run-field-${field.name}`"
          :label="humanize(field.name)"
          :model-value="stringValue(field.name)"
          :required="field.required"
          :values="field.enumValues ?? []"
          @update:model-value="fieldValues[field.name] = $event"
        />
        <v-text-field
          v-else
          :data-testid="`job-run-field-${field.name}`"
          :hint="field.required ? 'Required' : 'Optional'"
          :label="humanize(field.name)"
          :model-value="stringValue(field.name) ?? ''"
          :type="isNumeric(field) ? 'number' : 'text'"
          persistent-hint
          @update:model-value="fieldValues[field.name] = $event"
        />
      </template>
    </template>

    <v-alert
      v-if="errorMessage"
      class="mt-3"
      data-testid="job-run-error"
      density="compact"
      type="error"
    >
      {{ errorMessage }}
    </v-alert>

    <p
      v-if="queuedType"
      class="job-run__queued"
      data-testid="job-run-queued"
      role="status"
    >
      {{ jobCatalogEntry(queuedType).title }} is queued.
    </p>

    <div class="job-run__actions">
      <v-btn
        :disabled="!selectedType || requiredMissing"
        :loading="submitting"
        color="primary"
        data-testid="job-run-submit"
        variant="flat"
        @click="submit"
      >
        Queue the job
      </v-btn>
    </div>
  </div>
</template>

<style scoped>
.job-run {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 36rem;
}

.job-run__queued {
  margin: 0.5rem 0 0;
  color: var(--color-ash);
}

.job-run__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 0.75rem;
}
</style>
