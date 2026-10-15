<script lang="ts" setup>
import {DateTime} from "luxon"
import {ref} from "vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import CutButton from "@/components/island/CutButton.vue"
import DateInput from "@/components/island/DateInput.vue"
import FormField from "@/components/island/FormField.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {type FieldChecks, useFormChecks} from "@/composables/useFormChecks"
import {MemberType, type MembershipResponse, type MembershipTypeResponse, recordTypeOn, removeTypeFrom} from "@/domains/user"
import {$showStatusMessage} from "@/plugins/handleNetworkError.ts"
import {memberTypeLabel} from "@/utils/memberType"
import {formatDay} from "@/utils/timestamps"

/* The types one membership has had, earliest first, as in "alumni since 1 September 2025". The
   board adds and removes them here; the first starts with the membership and stays. */
defineOptions({name: "MembershipTypes"})

const {membership} = defineProps<{membership: MembershipResponse}>()
const emit = defineEmits<{changed: [membership: MembershipResponse]}>()

const COLUMNS: TableColumn<MembershipTypeResponse>[] = [
  {key: "type", label: "Type", sortBy: (row) => memberTypeLabel(row.type)},
  {key: "since", label: "Starts on", sortBy: (row) => row.since},
]

const addTestid = `manage-membership-type-add-btn-${membership.id}`
const type = ref<string>(MemberType.ALUMNI)
const since = ref("")
const refusal = ref("")
const saving = ref(false)
// The end is the day the membership stopped, and no type starts after today. TWIN: `requireInside`
// in `user/domain/MembershipUseCases.kt` and `MembershipTypeRequest`'s @PastOrPresent.
const latest = membership.endDate
  ? DateTime.fromISO(membership.endDate).minus({days: 1}).toISODate()!
  : DateTime.now().toISODate()!

const checks = useFormChecks((): Record<string, FieldChecks> => ({
  since: {value: () => since.value, checks: [(value: unknown) => (value ? null : "Fill in the day this type starts.")]},
}))
const said = () => [...checks.errorsOf("since"), refusal.value].filter(Boolean).join(" ")

const add = async () => {
  if (saving.value || !checks.attempt()) return
  saving.value = true
  refusal.value = ""
  try {
    const answer = await recordTypeOn(membership.id, {type: type.value as MemberType, since: since.value})
    if (answer.ok) emit("changed", answer.saved)
    else refusal.value = answer.reason
  } finally {
    saving.value = false
  }
}

const remove = async (entry: MembershipTypeResponse) => {
  const answer = await removeTypeFrom(membership.id, entry.id)
  if (answer.ok) emit("changed", answer.saved)
  else $showStatusMessage(answer.reason)
}
</script>

<template>
  <div
    class="membership-types"
    :data-testid="`manage-membership-types-${membership.id}`"
  >
    <list-head title="Membership types" />
    <management-table
      :columns="COLUMNS"
      :row-key="(entry) => entry.id"
      :row-testid="(entry) => `manage-membership-type-row-${entry.id}`"
      :rows="membership.types"
      search-label="Search membership types"
      :search-text="(entry) => `${memberTypeLabel(entry.type)} ${formatDay(entry.since)}`"
    >
      <template #type="{row}">
        {{ memberTypeLabel(row.type) }}
      </template>
      <template #since="{row}">
        {{ formatDay(row.since) }}
      </template>
      <template #acts="{row}">
        <mini-button
          v-if="row.id !== membership.types[0]?.id"
          :testid="`manage-membership-type-remove-btn-${row.id}`"
          tone="danger"
          @click="remove(row)"
        >
          Remove
        </mini-button>
      </template>
    </management-table>

    <list-head title="Add a membership type" />
    <p class="membership-types__hint">
      A type added on a day that already has one replaces it.
    </p>
    <form
      class="membership-types__fields"
      novalidate
      @submit.prevent="add"
    >
      <member-type-select
        v-model="type"
        :testid="`${addTestid}-type`"
      />
      <form-field
        v-slot="field"
        :error="said()"
        filled
        label="Starts on"
        required
        :testid="`${addTestid}-since`"
        variant="inside"
      >
        <date-input
          v-model="since"
          :control-id="field.controlId"
          :invalid="field.invalid"
          :max="latest"
          :min="membership.startDate"
        />
      </form-field>
      <cut-button
        class="membership-types__save"
        :disabled="saving"
        submit
        :testid="addTestid"
        tone="solid"
      >
        {{ saving ? "Saving" : "Add type" }}
      </cut-button>
    </form>
  </div>
</template>

<style scoped>
.membership-types {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-top: 1rem;
}

.membership-types__hint {
  font-size: 0.9rem;
  color: var(--color-ash);
}

/* One row, as the membership's own fields are. */
.membership-types__fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr)) auto;
  gap: 0.4rem 1.2rem;
  align-items: start;
}

.membership-types__save {
  margin-top: 0.35rem;
}

@media (--phone) {
  .membership-types__fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
