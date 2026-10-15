<script lang="ts" setup>
/* A membership type as the board adds or corrects it: which type, and from which day. The first
   type starts with the membership, so its day is fixed. */
import {DateTime} from "luxon"
import {ref} from "vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import CutButton from "@/components/island/CutButton.vue"
import DateInput from "@/components/island/DateInput.vue"
import FormField from "@/components/island/FormField.vue"
import {type FieldChecks, useFormChecks} from "@/composables/useFormChecks"
import {correctTypeOn, MemberType, type MembershipResponse, type MembershipTypeResponse, recordTypeOn} from "@/domains/user"

defineOptions({name: "MembershipTypeFields"})

const {membership, entry = undefined, submitText, submitTestid} = defineProps<{
  membership: MembershipResponse
  /** The type being corrected, or nothing for a new one. */
  entry?: MembershipTypeResponse
  submitText: string
  submitTestid: string
}>()

const emit = defineEmits<{saved: [membership: MembershipResponse]}>()

const first = entry != null && entry.id === membership.types[0]?.id
const type = ref<string>(entry?.type ?? MemberType.ALUMNI)
const since = ref(entry?.since ?? "")
const refusal = ref("")
// The end is the day the membership stopped, and no type starts after today. TWIN: `requireTypesFit` in
// `user/domain/MembershipUseCases.kt` and `MembershipTypeRequest`'s @PastOrPresent.
const latest = membership.endDate
  ? DateTime.fromISO(membership.endDate).minus({days: 1}).toISODate()!
  : DateTime.now().toISODate()!
const saving = ref(false)

const checks = useFormChecks((): Record<string, FieldChecks> => ({
  since: {value: () => since.value, checks: [(value: unknown) => (value ? null : "Fill in the day this type starts.")]},
}))
const said = () => [...checks.errorsOf("since"), refusal.value].filter(Boolean).join(" ")

const save = async () => {
  if (saving.value || !checks.attempt()) return
  saving.value = true
  refusal.value = ""
  const body = {type: type.value as MemberType, since: since.value}
  try {
    const answer = entry ? await correctTypeOn(membership.id, entry.id, body) : await recordTypeOn(membership.id, body)
    if (answer.ok) emit("saved", answer.saved)
    else refusal.value = answer.reason
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form
    class="type-fields"
    novalidate
    @submit.prevent="save"
  >
    <member-type-select
      v-model="type"
      :testid="`${submitTestid}-type`"
    />
    <form-field
      v-slot="field"
      :error="said()"
      filled
      label="Starts on"
      required
      :testid="`${submitTestid}-since`"
      variant="inside"
    >
      <date-input
        v-model="since"
        :control-id="field.controlId"
        :disabled="first"
        :invalid="field.invalid"
        :max="latest"
        :min="membership.startDate"
      />
    </form-field>
    <cut-button
      class="type-fields__save"
      :disabled="saving"
      submit
      :testid="submitTestid"
      tone="solid"
    >
      {{ saving ? "Saving" : submitText }}
    </cut-button>
  </form>
</template>

<style scoped>
/* One row, as the membership's own fields are. */
.type-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr)) auto;
  gap: 0.4rem 1.2rem;
  align-items: start;
}

.type-fields__save {
  margin-top: 0.35rem;
}

@media (--phone) {
  .type-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
