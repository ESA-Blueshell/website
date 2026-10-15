<script lang="ts" setup>
import {DateTime} from "luxon"
import {computed, ref} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import FormFields from "@/components/island/FormFields.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import SegmentedChoice from "@/components/island/SegmentedChoice.vue"
import {BookingKind, type Choice, type Expense} from "../declarations"
import ReceiptPicker from "./ReceiptPicker.vue"

/** One expense of a declaration being written: the day, the amount, what for and its receipts. */
defineOptions({name: "ExpenseEditor"})

const {number, removable, committees, events, testid, errors = {}} = defineProps<{
  /** Counted from 1, as the sheet and the missing list name it. */
  number: number
  removable: boolean
  committees: Choice[]
  events: Choice[]
  testid: string
  /** What each field is missing, shown on the field itself. */
  errors?: {paidOn?: string[]; amount?: string[]; description?: string[]; receipts?: string[]}
}>()

const emit = defineEmits<{remove: []; touch: [field: "paidOn" | "amount" | "description"]}>()

const expense = defineModel<Expense>({required: true})

const today = DateTime.now().toISODate()!

const set = <K extends keyof Expense>(key: K, value: Expense[K]) => {
  expense.value = {...expense.value, [key]: value}
}

const FOR_OPTIONS = [
  {key: "none", label: "Neither"},
  {key: BookingKind.COMMITTEE, label: "A committee"},
  {key: BookingKind.EVENT, label: "An event"},
]

/* The kind is kept apart from the choice, so picking "A committee" opens the picker before one
   is chosen. */
const pendingKind = ref<"none" | BookingKind>("none")
const kind = computed(() => expense.value.for?.kind ?? pendingKind.value)

const chooseKind = (key: string) => {
  pendingKind.value = key as "none" | BookingKind
  set("for", null)
}

const options = computed(() => (kind.value === BookingKind.EVENT ? events : committees)
  .map(one => ({key: String(one.id), label: one.name, ...(one.note ? {note: one.note} : {})})))

const pick = (key: string) => {
  const which = kind.value === BookingKind.EVENT ? BookingKind.EVENT : BookingKind.COMMITTEE
  const found = (which === BookingKind.EVENT ? events : committees).find(one => String(one.id) === key)
  if (found) set("for", {kind: which, id: found.id, name: found.name})
}
</script>

<template>
  <section
    class="island-panel expense"
    :data-testid="testid"
  >
    <header class="expense__head">
      <h3 class="island-panel__title">
        Expense {{ number }}
      </h3>
      <button
        v-if="removable"
        class="form-link"
        :data-testid="`${testid}-remove`"
        type="button"
        @click="emit('remove')"
      >
        Remove
      </button>
    </header>

    <form-fields>
      <form-control
        :error-messages="errors.paidOn"
        label="Date you paid*"
        :max="today"
        :model-value="expense.paidOn"
        :testid="`${testid}-day`"
        type="date"
        @blur="emit('touch', 'paidOn')"
        @update:model-value="set('paidOn', $event ?? '')"
      />
      <form-control
        :error-messages="errors.amount"
        kind="money"
        label="Amount*"
        :model-value="expense.amount"
        :testid="`${testid}-amount`"
        @blur="emit('touch', 'amount')"
        @update:model-value="set('amount', $event ?? '')"
      />
      <div class="form-span">
        <form-control
          hint="What you bought and why, so the board knows what it was for."
          :error-messages="errors.description"
          kind="textarea"
          label="Description*"
          :model-value="expense.description"
          :testid="`${testid}-description`"
          @blur="emit('touch', 'description')"
          @update:model-value="set('description', $event ?? '')"
        />
      </div>
    </form-fields>

    <div class="expense__part">
      <p class="expense__label">
        For a committee or event
      </p>
      <segmented-choice
        :model-value="kind"
        :options="FOR_OPTIONS"
        :testid-prefix="`${testid}-for`"
        @update:model-value="chooseKind"
      />
      <form-field
        v-if="kind !== 'none'"
        :filled="expense.for != null"
        :label="kind === BookingKind.EVENT ? 'Event' : 'Committee'"
        :testid="`${testid}-for-pick`"
        variant="inside"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :empty-note="kind === BookingKind.EVENT ? 'There are no events to choose from.' : 'There are no committees to choose from.'"
            :labelled-by="labelId"
            :options="options"
            :selected-key="expense.for ? String(expense.for.id) : null"
            :testid-prefix="`${testid}-for-picker`"
            @pick="pick"
          />
        </template>
      </form-field>
    </div>

    <div class="expense__part">
      <p
        class="expense__label"
        :class="{'expense__label--wrong': errors.receipts?.length}"
      >
        Receipts
      </p>
      <receipt-picker
        :invalid="Boolean(errors.receipts?.length)"
        :model-value="expense.receipts"
        :testid="testid"
        :wrong="errors.receipts?.[0] ?? null"
        @update:model-value="set('receipts', $event)"
      />
    </div>
  </section>
</template>

<style scoped>
.expense__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

.expense__part {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding-top: 0.4rem;
}

/* As a field's label reads once it is filled. */
.expense__label {
  font-size: 0.62rem;
  font-weight: 500;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.expense__label--wrong {
  color: var(--color-wrong);
}

</style>
