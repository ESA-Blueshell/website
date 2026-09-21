<script setup lang="ts">
/** One of the events, newest first, since that is the one somebody is usually after. */
import {computed, onMounted, ref} from "vue"
import IslandField from "@/components/island/IslandField.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {type EventResponse, listEvents} from "@/domains/events"

const {
  modelValue = undefined,
  label = "Event",
  required = false,
  disabled = false,
  errorMessages = undefined,
  testid = undefined,
} = defineProps<{
  modelValue?: number | undefined
  label?: string
  required?: boolean
  disabled?: boolean
  /** What the api or a rule found wrong, in the shape every other field is handed it. */
  errorMessages?: string | string[]
  testid?: string
}>()

const said = computed<string>(() => {
  const first = Array.isArray(errorMessages) ? errorMessages[0] : errorMessages
  return first ?? ""
})

const emit = defineEmits<{"update:modelValue": [value: number | undefined]}>()

const held = ref<EventResponse[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const content = await listEvents()
    held.value = content.slice()
      .sort((a, b) => (b.startTime ?? "").localeCompare(a.startTime ?? ""))
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})

const dayOf = (event: EventResponse): string =>
  (event.startTime ? new Date(event.startTime).toLocaleDateString() : "")

const options = computed(() => held.value.map(one => ({
  key: String(one.id),
  label: one.title ?? `Event #${one.id}`,
  note: dayOf(one),
  terms: [one.location ?? "", dayOf(one)].filter(said => said !== ""),
})))
</script>

<template>
  <island-field
    :error="said"
    :filled="modelValue != null"
    :label="label"
    :required="required"
    :testid="testid"
    variant="inside"
  >
    <island-picker
      :disabled="disabled"
      empty-note="There are no events yet."
      :loading="loading"
      :options="options"
      :selected-key="modelValue == null ? null : String(modelValue)"
      :testid-prefix="testid ?? 'event-picker'"
      @pick="emit('update:modelValue', Number($event))"
    />
  </island-field>
</template>
