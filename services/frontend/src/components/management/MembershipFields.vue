<script lang="ts" setup>
/* A membership as the board adds or edits it: when it starts, when it ends and what kind it is,
   on one row. How the person pays is not set here. */
import {vFirstField} from "@/utils/firstField"
import {ref} from "vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import CutButton from "@/components/island/CutButton.vue"
import DateInput from "@/components/island/DateInput.vue"
import FormField from "@/components/island/FormField.vue"
import {type FieldChecks, reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {MemberType, type MembershipResponse, saveMembership, startMembershipAsBoard} from "@/domains/user"

defineOptions({name: "MembershipFields"})

const {userId, membership = undefined, submitText, submitTestid} = defineProps<{
  userId: number
  /** The membership being edited, or nothing for a new one. */
  membership?: MembershipResponse
  submitText: string
  submitTestid: string
}>()

const emit = defineEmits<{saved: [membership: MembershipResponse]}>()

const startDate = ref(membership?.startDate ?? "")
const endDate = ref(membership?.endDate ?? "")
const memberType = ref<string>(membership?.memberType ?? MemberType.REGULAR)
const saving = ref(false)

const checks = useFormChecks((): Record<string, FieldChecks> => ({
  startDate: {value: () => startDate.value, checks: [(value: unknown) => (value ? null : "Fill in the start date.")]},
  endDate: {value: () => endDate.value, checks: []},
  memberType: {value: () => memberType.value, checks: []},
}))
const said = (name: string) => checks.errorsOf(name).join(" ")

const save = async () => {
  if (saving.value || !checks.attempt()) return
  saving.value = true
  const fields = {userId, startDate: startDate.value, endDate: endDate.value || null, memberType: memberType.value as MemberType}
  try {
    // A new membership pays by transfer; an edit keeps how it was paid.
    emit("saved", membership
      ? await saveMembership(membership.id, {...membership, ...fields})
      : await startMembershipAsBoard(userId, {...fields, incasso: false}))
  } catch (error) {
    reportRefusal(checks, error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form
    v-first-field
    class="membership-fields"
    novalidate
    @submit.prevent="save"
  >
    <form-field
      v-slot="field"
      :error="said('startDate')"
      filled
      label="Start date"
      required
      testid="membership-form-start-date"
      variant="inside"
    >
      <date-input
        v-model="startDate"
        :control-id="field.controlId"
        :invalid="field.invalid"
      />
    </form-field>
    <form-field
      v-slot="field"
      :error="said('endDate')"
      filled
      label="End date"
      testid="membership-form-end-date"
      variant="inside"
    >
      <date-input
        v-model="endDate"
        :control-id="field.controlId"
        :invalid="field.invalid"
        :min="startDate || undefined"
      />
    </form-field>
    <member-type-select
      v-model="memberType"
      :error-messages="checks.errorsOf('memberType')"
      testid="membership-form-member-type"
    />
    <cut-button
      class="membership-fields__save"
      :data-submit-mode="membership ? 'update' : 'create'"
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
/* One row: the three fields share the width and the button keeps its own. */
.membership-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  gap: 0.4rem 1.2rem;
  align-items: start;
}

/* Level with the fields' boxes, not with the message line under them. */
.membership-fields__save {
  margin-top: 0.35rem;
}

@media (--phone) {
  .membership-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
