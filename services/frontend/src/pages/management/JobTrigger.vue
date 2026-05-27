<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {type JobPayloadField, type JobTypeDescriptor, enqueue, jobTypes as fetchJobTypes} from "@/services/api"

defineOptions({name: "JobTriggerPage"})

const descriptors = ref<JobTypeDescriptor[]>([])
const selectedType = ref<string | null>(null)
const fieldValues = ref<Record<string, string>>({})
const loading = ref<boolean>(false)
const submitting = ref<boolean>(false)
const resultMessage = ref<string | null>(null)
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

const loadTypes = async () => {
  loading.value = true
  try {
    const response = await fetchJobTypes()
    if (response.status === 200 && Array.isArray(response.data)) {
      descriptors.value = response.data as JobTypeDescriptor[]
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

watch(selectedType, () => {
  fieldValues.value = {}
  resultMessage.value = null
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
  if (!selectedType.value) return
  submitting.value = true
  resultMessage.value = null
  errorMessage.value = null
  try {
    const response = await enqueue({body: {jobType: selectedType.value, payload: buildPayload()}})
    if (response.status === 200 && response.data) {
      const job = response.data as {id?: number; status?: string}
      resultMessage.value = `Enqueued job #${job.id} (${job.status})`
    } else {
      errorMessage.value = "Failed to enqueue job"
    }
  } catch (error) {
    errorMessage.value = (error as Error)?.message ?? "Failed to enqueue job"
    $handleNetworkError(error)
  } finally {
    submitting.value = false
  }
}

onMounted(loadTypes)
</script>

<template>
  <v-container>
    <h1 class="text-h5 mb-4">
      Trigger a job
    </h1>
    <v-card
      class="pa-4"
      max-width="640"
    >
      <v-select
        v-model="selectedType"
        :items="typeOptions"
        item-title="title"
        item-value="value"
        label="Job type"
        :loading="loading"
        data-testid="job-trigger-type"
      />

      <template v-if="selectedDescriptor">
        <p
          v-if="selectedDescriptor.payloadFields.length === 0"
          class="text-medium-emphasis mb-2"
        >
          This job takes no payload.
        </p>
        <v-text-field
          v-for="field in selectedDescriptor.payloadFields"
          :key="field.name"
          v-model="fieldValues[field.name]"
          :label="`${field.name} (${field.type})`"
          :type="isNumeric(field) ? 'number' : 'text'"
          :hint="field.required ? 'required' : 'optional'"
          persistent-hint
          :data-testid="`job-trigger-field-${field.name}`"
        />
      </template>

      <v-btn
        class="mt-4"
        color="primary"
        :disabled="!selectedType"
        :loading="submitting"
        data-testid="job-trigger-submit"
        @click="submit"
      >
        Trigger job
      </v-btn>

      <v-alert
        v-if="resultMessage"
        type="success"
        class="mt-4"
        data-testid="job-trigger-success"
      >
        {{ resultMessage }}
      </v-alert>
      <v-alert
        v-if="errorMessage"
        type="error"
        class="mt-4"
        data-testid="job-trigger-error"
      >
        {{ errorMessage }}
      </v-alert>
    </v-card>
  </v-container>
</template>
