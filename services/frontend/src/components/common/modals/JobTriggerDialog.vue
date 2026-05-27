<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {type JobPayloadField, type JobTypeDescriptor, enqueue, jobTypes as fetchJobTypes} from "@/services/api"

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
const fieldValues = ref<Record<string, string>>({})
const submitting = ref<boolean>(false)
const errorMessage = ref<string | null>(null)

const humanize = (value: string): string =>
  value
    .replace(/[._-]+/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1).toLowerCase())
    .join(" ")

const typeOptions = computed(() =>
  descriptors.value.map((descriptor) => ({title: humanize(descriptor.type), value: descriptor.type})),
)

const selectedDescriptor = computed<JobTypeDescriptor | null>(
  () => descriptors.value.find((descriptor) => descriptor.type === selectedType.value) ?? null,
)

const NUMERIC_TYPES = new Set(["Long", "Int", "Integer", "Short", "Double", "Float", "BigDecimal", "BigInteger"])
const isNumeric = (field: JobPayloadField): boolean => NUMERIC_TYPES.has(field.type)

const requiredMissing = computed<boolean>(() =>
  (selectedDescriptor.value?.payloadFields ?? []).some(
    (field) => field.required && !(fieldValues.value[field.name]?.trim()),
  ),
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
    const raw = fieldValues.value[field.name]
    if (raw === undefined || raw.trim() === "") continue
    if (field.type === "Boolean") payload[field.name] = raw.trim().toLowerCase() === "true"
    else if (isNumeric(field)) payload[field.name] = Number(raw)
    else payload[field.name] = raw
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

        <template v-if="selectedDescriptor">
          <p
            v-if="selectedDescriptor.payloadFields.length === 0"
            class="text-medium-emphasis mb-0"
          >
            This job takes no arguments.
          </p>
          <v-text-field
            v-for="field in selectedDescriptor.payloadFields"
            :key="field.name"
            v-model="fieldValues[field.name]"
            :data-testid="`job-trigger-field-${field.name}`"
            :hint="field.required ? 'Required' : 'Optional'"
            :label="humanize(field.name)"
            :type="isNumeric(field) ? 'number' : 'text'"
            persistent-hint
          />
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
