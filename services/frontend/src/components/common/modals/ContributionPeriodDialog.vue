<template>
  <v-dialog
    v-model="showDialog"
    data-testid="contribution-period-dialog"
    max-width="600"
  >
    <v-card>
      <v-card-title class="mt-6 align-center justify-center text-center">
        <span class="text-h4">
          {{ editedPeriodId ? "Edit Contribution Period" : "Add Contribution Period" }}
        </span>
      </v-card-title>

      <v-card-text>
        <v-row dense>
          <v-col cols="6">
            <form-control
              v-model="periodForm.startDate"
              data-testid="contribution-period-start-date-field"
              :error-messages="errorsOf('startDate')"
              kind="date"
              label="Start Date"
              @blur="touch('startDate')"
            />
          </v-col>
          <v-col cols="6">
            <form-control
              v-model="periodForm.endDate"
              data-testid="contribution-period-end-date-field"
              :error-messages="errorsOf('endDate')"
              kind="date"
              label="End Date"
              @blur="touch('endDate')"
            />
          </v-col>
        </v-row>

        <form-control
          v-model="periodForm.halfYearCutoffDate"
          data-testid="contribution-period-half-year-cutoff-field"
          :error-messages="errorsOf('halfYearCutoffDate')"
          hint="A regular membership starting after this date pays the half-year fee; one starting on it or before pays the full year."
          kind="date"
          label="Half Year Cutoff Date"
          @blur="touch('halfYearCutoffDate')"
        />

        <form-control
          data-testid="contribution-period-half-year-fee-field"
          :error-messages="errorsOf('halfYearFee')"
          kind="number"
          label="Half Year Fee"
          :model-value="String(periodForm.halfYearFee)"
          step="0.01"
          inputmode="decimal"
          @blur="touch('halfYearFee')"
          @update:model-value="(raw: string | null) => (periodForm.halfYearFee = raw ? Number(raw) : 0)"
        />

        <form-control
          data-testid="contribution-period-full-year-fee-field"
          :error-messages="errorsOf('fullYearFee')"
          kind="number"
          label="Full Year Fee"
          :model-value="String(periodForm.fullYearFee)"
          step="0.01"
          inputmode="decimal"
          @blur="touch('fullYearFee')"
          @update:model-value="(raw: string | null) => (periodForm.fullYearFee = raw ? Number(raw) : 0)"
        />

        <form-control
          data-testid="contribution-period-alumni-fee-field"
          :error-messages="errorsOf('alumniFee')"
          kind="number"
          label="Alumni Fee"
          :model-value="String(periodForm.alumniFee)"
          step="0.01"
          inputmode="decimal"
          @blur="touch('alumniFee')"
          @update:model-value="(raw: string | null) => (periodForm.alumniFee = raw ? Number(raw) : 0)"
        />
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          v-if="editedPeriodId"
          color="red"
          data-testid="contribution-period-delete-btn"
          @click="confirmDeletePeriod"
        >
          Delete
        </v-btn>
        <v-btn
          color="primary"
          :data-submit-mode="editedPeriodId ? 'update' : 'create'"
          data-testid="contribution-period-submit-btn"
          @click="saveContributionPeriod"
        >
          {{ editedPeriodId ? "Save" : "Create" }}
        </v-btn>
        <v-btn
          data-testid="contribution-period-cancel-btn"
          @click="closeDialog"
        >
          Cancel
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import {
  type ContributionPeriodResponse,
  type CreateContributionPeriodRequest,
  saveNewPeriod,
  savePeriod,
  type UpdateContributionPeriodRequest,
} from "@/domains/contribution"
import {reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {dateAfter, dateBefore, dateMax, dateMin, minValue, required} from "@/utils/checks"

defineOptions({name: "ContributionPeriodDialog"})

type PeriodFormModel = CreateContributionPeriodRequest & Partial<ContributionPeriodResponse>

const props = defineProps<{ contributionPeriod?: ContributionPeriodResponse; showDialog: boolean }>()
const emit = defineEmits<{
  (e: "update:showDialog", value: boolean): void;
  (e: "changed", value: ContributionPeriodResponse): void;
  (e: "delete", value: number): void;
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
const checks = useFormChecks(() => {
  const form = periodForm.value
  return {
    startDate: {value: () => form.startDate, checks: [required, dateBefore(() => form.endDate)]},
    endDate: {value: () => form.endDate, checks: [required, dateAfter(() => form.startDate)]},
    halfYearCutoffDate: {
      value: () => form.halfYearCutoffDate,
      checks: [required, dateMin(() => form.startDate), dateMax(() => form.endDate)],
    },
    halfYearFee: {value: () => form.halfYearFee, checks: [required, minValue(0)]},
    fullYearFee: {value: () => form.fullYearFee, checks: [required, minValue(0)]},
    alumniFee: {value: () => form.alumniFee, checks: [required, minValue(0)]},
  }
})
const {errorsOf, touch} = checks
const editedPeriodId = computed(() => props.contributionPeriod?.id)

/**
 * A whole new object every time. Merging into the previous one would keep the keys the empty
 * period does not name — id, version, contactListId — so adding a period after editing one
 * would carry that period's identity and update it instead.
 */
const loadPeriod = (val?: ContributionPeriodResponse | null) => {
  periodForm.value = val ? {...val} : emptyPeriod()
  checks.settle()
}

watch(() => props.contributionPeriod, (val) => loadPeriod(val), {immediate: true})

watch(
  () => props.showDialog,
  (open) => {
    if (open) loadPeriod(props.contributionPeriod)
  },
)

const showDialog = computed({
  get: () => props.showDialog,
  set: (value: boolean) => emit("update:showDialog", value),
})

const closeDialog = () => {
  showDialog.value = false
}
const confirmDeletePeriod = () => {
  showDialog.value = false
  if (editedPeriodId.value != null) emit("delete", editedPeriodId.value)
}

const saveContributionPeriod = async () => {
  if (!checks.attempt()) return

  const form = periodForm.value
  const fees = {
    startDate: form.startDate,
    endDate: form.endDate,
    halfYearCutoffDate: form.halfYearCutoffDate,
    halfYearFee: form.halfYearFee,
    fullYearFee: form.fullYearFee,
    alumniFee: form.alumniFee,
    contactListId: form.contactListId,
  }

  try {
    // The prop says which period is open, and only that decides create against update. The
    // form is editable, so a value in it is no statement about which row exists.
    if (props.contributionPeriod?.id) {
      const payload: UpdateContributionPeriodRequest = {
        ...fees,
        version: props.contributionPeriod.version,
      }
      emit("changed", await savePeriod(props.contributionPeriod.id, payload))
      closeDialog()
    } else {
      const payload: CreateContributionPeriodRequest = {...fees}
      emit("changed", await saveNewPeriod(payload))
      closeDialog()
    }
  } catch (err) {
    reportRefusal(checks, err)
  }
}
</script>
