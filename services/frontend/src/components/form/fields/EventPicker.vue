<script setup lang="ts">
/** One of the events, newest first, since that is the one somebody is usually after. */
import {computed, onMounted, ref} from "vue"
import {firstSaid} from "@/components/form/fields/saidWrong"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
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
  errorMessages?: string | string[]
  testid?: string
}>()

const said = computed<string>(() => firstSaid(errorMessages))

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
  <form-field
    :error="said"
    :filled="modelValue != null"
    :label="label"
    :required="required"
    :testid="testid"
    variant="inside"
  >
    <template #default="{controlId, labelId}">
      <search-picker
        :control-id="controlId"
        :labelled-by="labelId"
        :disabled="disabled"
        empty-note="There are no events yet."
        :loading="loading"
        :options="options"
        :selected-key="modelValue == null ? null : String(modelValue)"
        :testid-prefix="testid ?? 'event-picker'"
        @pick="emit('update:modelValue', Number($event))"
      />
    </template>
  </form-field>
</template>
