<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {type JobPayloadField, type JobTypeDescriptor, enqueue, jobTypes as fetchJobTypes} from "@/services/api"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import CohortPicker from "@/components/form/fields/CohortPicker.vue"
import EventPicker from "@/components/form/fields/EventPicker.vue"
import ContributionPeriodPicker from "@/components/form/fields/ContributionPeriodPicker.vue"
import EnumPicker from "@/components/form/fields/EnumPicker.vue"
import {humanizeJobType, jobCatalogEntry} from "@/utils/jobCatalog"

const props = defineProps<{modelValue: boolean}>()
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "enqueued", jobType: string): void;
}>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value),
})

const descriptors = ref<JobTypeDescriptor[]>([])
const typesLoaded = ref<boolean>(false)
const loadingTypes = ref<boolean>(false)
const selectedType = ref<string | null>(null)
// Stores either the raw text input (for free-text/numeric fields) or
// the picked id/enum value (for picker fields). Values are coerced in
// buildPayload below.
const fieldValues = ref<Record<string, unknown>>({})
const submitting = ref<boolean>(false)
const errorMessage = ref<string | null>(null)

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
 * `userId` / `cohortId` / `eventId` / `contributionPeriodId` (or
 * `periodId`) gets the matching picker. Keeps the JobTriggerDialog
 * blind to specific job payload shapes — any future field that
 * follows the same naming convention picks up the same picker.
 */
const pickerForField = (field: JobPayloadField): PickerKind => {
  if (!isNumeric(field)) return null
  const name = field.name.toLowerCase()
  if (name === "userid" || name.endsWith("userid")) return "user"
  if (name === "cohortid" || name.endsWith("cohortid")) return "cohort"
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
  if (typesLoaded.value) return
  loadingTypes.value = true
  try {
    const response = await fetchJobTypes()
    if (response.status === 200 && Array.isArray(response.data)) {
      descriptors.value = (response.data as JobTypeDescriptor[])
        .slice()
        .sort((a, b) => a.type.localeCompare(b.type))
      typesLoaded.value = true
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loadingTypes.value = false
  }
}

const reset = () => {
  selectedType.value = null
  fieldValues.value = {}
  errorMessage.value = null
  submitting.value = false
}

watch(open, (isOpen) => {
  if (isOpen) loadTypes()
  else reset()
})

watch(selectedType, () => {
  fieldValues.value = {}
  errorMessage.value = null
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
    const response = await enqueue({body: {jobType: selectedType.value, payload: buildPayload()}})
    if (response.status === 200 && response.data) {
      emit("enqueued", selectedType.value)
      open.value = false
    } else {
      errorMessage.value = "Failed to trigger job."
    }
  } catch (error) {
    errorMessage.value = (error as Error)?.message ?? "Failed to trigger job."
    $handleNetworkError(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <v-dialog
    v-model="open"
    data-testid="job-trigger-dialog"
    max-width="560"
  >
    <v-card>
      <v-card-title class="text-h6">
        Trigger a job
      </v-card-title>

      <v-card-text>
        <v-select
          v-model="selectedType"
          :items="typeOptions"
          :loading="loadingTypes"
          data-testid="job-trigger-type"
          item-title="title"
          item-value="value"
          label="Job type"
        />

        <!-- Plain-text description below the type select; mounted always so
             choosing a job type does not shift the payload form. -->
        <p
          v-show="selectedDescription"
          class="text-caption text-medium-emphasis mt-n2 mb-3"
          data-testid="job-trigger-description"
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
            <!-- Reusable id pickers; the JobTriggerDialog itself stays generic
                 and any new payload field that follows the naming convention
                 (xxxUserId / xxxCohortId / xxxEventId / xxxPeriodId) picks
                 up the same control automatically. -->
            <UserPicker
              v-if="pickerForField(field) === 'user'"
              :data-testid="`job-trigger-field-${field.name}`"
              :label="humanize(field.name)"
              :model-value="numberValue(field.name)"
              :required="field.required"
              @update:model-value="fieldValues[field.name] = $event"
            />
            <CohortPicker
              v-else-if="pickerForField(field) === 'cohort'"
              :data-testid="`job-trigger-field-${field.name}`"
              :label="humanize(field.name)"
              :model-value="numberValue(field.name)"
              :required="field.required"
              @update:model-value="fieldValues[field.name] = $event"
            />
            <EventPicker
              v-else-if="pickerForField(field) === 'event'"
              :data-testid="`job-trigger-field-${field.name}`"
              :label="humanize(field.name)"
              :model-value="numberValue(field.name)"
              :required="field.required"
              @update:model-value="fieldValues[field.name] = $event"
            />
            <ContributionPeriodPicker
              v-else-if="pickerForField(field) === 'contributionPeriod'"
              :data-testid="`job-trigger-field-${field.name}`"
              :label="humanize(field.name)"
              :model-value="numberValue(field.name)"
              :required="field.required"
              @update:model-value="fieldValues[field.name] = $event"
            />
            <EnumPicker
              v-else-if="isEnum(field)"
              :data-testid="`job-trigger-field-${field.name}`"
              :label="humanize(field.name)"
              :model-value="stringValue(field.name)"
              :required="field.required"
              :values="field.enumValues ?? []"
              @update:model-value="fieldValues[field.name] = $event"
            />
            <v-text-field
              v-else
              :data-testid="`job-trigger-field-${field.name}`"
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
          data-testid="job-trigger-error"
          density="compact"
          type="error"
        >
          {{ errorMessage }}
        </v-alert>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          data-testid="job-trigger-cancel"
          @click="open = false"
        >
          Cancel
        </v-btn>
        <v-btn
          :disabled="!selectedType || requiredMissing"
          :loading="submitting"
          color="primary"
          data-testid="job-trigger-submit"
          variant="flat"
          @click="submit"
        >
          Trigger
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
