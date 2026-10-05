<template>
  <form
    v-first-field
    class="period-form"
    data-testid="contribution-period-dialog"
    novalidate
    @submit.prevent="saveContributionPeriod"
  >
    <list-head title="Dates" />
    <div class="period-form__row">
      <form-field
        v-slot="field"
        :error="shown('startDate')"
        label="Start date"
        required
        testid="contribution-period-start-date-field"
      >
        <date-input
          v-model="periodForm.startDate"
          :control-id="field.controlId"
          :invalid="field.invalid"
        />
      </form-field>
      <form-field
        v-slot="field"
        :error="shown('endDate')"
        label="End date"
        required
        testid="contribution-period-end-date-field"
      >
        <date-input
          v-model="periodForm.endDate"
          :control-id="field.controlId"
          :invalid="field.invalid"
        />
      </form-field>
      <form-field
        v-slot="field"
        :error="shown('halfYearCutoffDate')"
        hint="A regular membership that starts after this date pays the half-year fee."
        label="Half-year cutoff date"
        required
        testid="contribution-period-half-year-cutoff-field"
      >
        <date-input
          v-model="periodForm.halfYearCutoffDate"
          :control-id="field.controlId"
          :invalid="field.invalid"
          :max="periodForm.endDate || undefined"
          :min="periodForm.startDate || undefined"
        />
      </form-field>
    </div>

    <list-head title="Fees" />
    <div class="period-form__row">
      <form-field
        v-for="fee in FEES"
        :key="fee.key"
        v-slot="field"
        :error="shown(fee.key)"
        :label="fee.label"
        :testid="`contribution-period-${fee.testid}-field`"
      >
        <money-input
          v-model="fees[fee.key]"
          :control-id="field.controlId"
          :invalid="field.invalid"
        />
      </form-field>
    </div>

    <div class="period-form__acts">
      <cut-button
        :data-submit-mode="editedPeriodId ? 'update' : 'create'"
        submit
        testid="contribution-period-submit-btn"
        tone="solid"
      >
        {{ editedPeriodId ? "Save" : "Create" }}
      </cut-button>
      <cut-button
        testid="contribution-period-cancel-btn"
        tone="quiet"
        @click="closeDialog"
      >
        Cancel
      </cut-button>
    </div>
  </form>
</template>

<script lang="ts" setup>
/* A period's dates, three fees and half-year cutoff; empty for a new one. Deleting sits on the
   page's danger zone rather than here. */
import {vFirstField} from "@/utils/firstField"
import {computed, ref, watch} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import DateInput from "@/components/island/DateInput.vue"
import FormField from "@/components/island/FormField.vue"
import MoneyInput from "@/components/island/MoneyInput.vue"
import ListHead from "@/components/management/ListHead.vue"
import {
  type ContributionPeriodResponse,
  type CreateContributionPeriodRequest,
  saveNewPeriod,
  savePeriod,
  type UpdateContributionPeriodRequest,
} from "@/domains/contribution"
import {handleSubmitError} from "@/composables/formUtils"

defineOptions({name: "ContributionPeriodForm"})

type PeriodFormModel = CreateContributionPeriodRequest & Partial<ContributionPeriodResponse>
type FeeKey = "halfYearFee" | "fullYearFee" | "alumniFee"
type DateKey = "startDate" | "endDate" | "halfYearCutoffDate"

const FEES: {key: FeeKey; label: string; testid: string}[] = [
  {key: "halfYearFee", label: "Half-year fee", testid: "half-year-fee"},
  {key: "fullYearFee", label: "Full-year fee", testid: "full-year-fee"},
  {key: "alumniFee", label: "Alumni fee", testid: "alumni-fee"},
]

const props = defineProps<{ contributionPeriod?: ContributionPeriodResponse }>()
const emit = defineEmits<{
  (e: "cancelled"): void;
  (e: "changed", value: ContributionPeriodResponse): void;
}>()

const emptyPeriod = (): PeriodFormModel => ({
  startDate: "",
  endDate: "",
  halfYearCutoffDate: "",
  halfYearFee: 0,
  fullYearFee: 0,
  alumniFee: 0,
})

const periodForm = ref<PeriodFormModel>(emptyPeriod())
// The fees as they are typed; an emptied one is a fee of nothing.
const fees = ref<Record<FeeKey, string>>({halfYearFee: "", fullYearFee: "", alumniFee: ""})
const refused = ref<Partial<Record<string, string>>>({})
const tried = ref(false)
const editedPeriodId = computed(() => props.contributionPeriod?.id)

/**
 * A whole new object every time. Merging into the previous one would keep the keys the empty
 * period does not name (id, version, contactListId), so adding a period after editing one
 * would carry that period's identity and update it instead.
 */
const loadPeriod = (val?: ContributionPeriodResponse | null) => {
  periodForm.value = val ? {...val} : emptyPeriod()
  const {halfYearFee, fullYearFee, alumniFee} = periodForm.value
  fees.value = {halfYearFee: halfYearFee.toFixed(2), fullYearFee: fullYearFee.toFixed(2), alumniFee: alumniFee.toFixed(2)}
  refused.value = {}
  tried.value = false
}

watch(() => props.contributionPeriod, (val) => loadPeriod(val), {immediate: true})

/* The api holds the same rules; these say them before the request is made. */
const wrong = computed<Partial<Record<DateKey, string>>>(() => {
  const {startDate, endDate, halfYearCutoffDate} = periodForm.value
  const said: Partial<Record<DateKey, string>> = {}
  if (!startDate) said.startDate = "Fill in the start date."
  else if (endDate && startDate >= endDate) said.startDate = "The start date has to be before the end date."
  if (!endDate) said.endDate = "Fill in the end date."
  if (!halfYearCutoffDate) said.halfYearCutoffDate = "Fill in the half-year cutoff date."
  else if ((startDate && halfYearCutoffDate < startDate) || (endDate && halfYearCutoffDate > endDate)) {
    said.halfYearCutoffDate = "The cutoff date has to be inside the period."
  }
  return said
})

const shown = (key: DateKey | FeeKey): string => refused.value[key] ?? (tried.value ? wrong.value[key as DateKey] ?? "" : "")

const closeDialog = () => emit("cancelled")

const saveContributionPeriod = async () => {
  tried.value = true
  refused.value = {}
  if (Object.keys(wrong.value).length > 0) return

  const form = periodForm.value
  const body = {
    startDate: form.startDate,
    endDate: form.endDate,
    halfYearCutoffDate: form.halfYearCutoffDate,
    halfYearFee: Number(fees.value.halfYearFee),
    fullYearFee: Number(fees.value.fullYearFee),
    alumniFee: Number(fees.value.alumniFee),
    contactListId: form.contactListId,
  }

  try {
    // The prop says which period is open, and only that decides create against update. The
    // form is editable, so a value in it is no statement about which row exists.
    if (props.contributionPeriod?.id) {
      const payload: UpdateContributionPeriodRequest = {
        ...body,
        version: props.contributionPeriod.version,
      }
      emit("changed", await savePeriod(props.contributionPeriod.id, payload))
    } else {
      const payload: CreateContributionPeriodRequest = {...body}
      emit("changed", await saveNewPeriod(payload))
    }
  } catch (err) {
    handleSubmitError({
      values: body,
      setFieldError: (field, message) => {
        refused.value = {...refused.value, [field]: [message].flat().join(" ")}
      },
    }, err)
  }
}
</script>

<style scoped>
.period-form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.period-form__row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.4rem 1.2rem;
}

.period-form__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 0.4rem;
}

@media (--phone) {
  .period-form__row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
