<template>
  <v-dialog
    v-model="showDialog"
    data-testid="contribution-period-dialog"
    max-width="600"
  >
    <v-card>
      <v-card-title class="mt-6 align-center justify-center text-center">
        <span class="text-h4">
          {{ contributionPeriod?.id ? "Edit Contribution Period" : "Add Contribution Period" }}
        </span>
      </v-card-title>

      <v-card-text>
        <Form
          ref="formRef"
          as="div"
        >
          <v-row dense>
            <v-col cols="6">
              <VvField
                v-model="periodForm.startDate"
                :component-props="{ type: 'date', 'data-testid': 'contribution-period-start-date-field' }"
                label="Start Date"
                name="startDate"
                rules="required|dateBefore:@endDate"
              />
            </v-col>
            <v-col cols="6">
              <VvField
                v-model="periodForm.endDate"
                :component-props="{ type: 'date', 'data-testid': 'contribution-period-end-date-field' }"
                label="End Date"
                name="endDate"
                rules="required|dateAfter:@startDate"
              />
            </v-col>
          </v-row>

          <VvField
            v-model="periodForm.halfYearCutoffDate"
            :component-props="{ type: 'date', 'data-testid': 'contribution-period-half-year-cutoff-field' }"
            hint="A regular membership starting after this date pays the half-year fee; one starting on it or before pays the full year."
            label="Half Year Cutoff Date"
            name="halfYearCutoffDate"
            persistent-hint
            rules="required|dateMin:@startDate|dateMax:@endDate"
          />

          <VvField
            v-model="periodForm.halfYearFee"
            :component-props="{ type: 'number', step: '0.01', inputmode: 'decimal', 'data-testid': 'contribution-period-half-year-fee-field' }"
            :update="(raw: string, handle: HandleChange<number>) => handle(!raw ? 0 : Number(raw))"
            label="Half Year Fee"
            name="halfYearFee"
            rules="required|minValue:0"
          />

          <VvField
            v-model="periodForm.fullYearFee"
            :component-props="{ type: 'number', step: '0.01', inputmode: 'decimal', 'data-testid': 'contribution-period-full-year-fee-field' }"
            :update="(raw: string, handle: HandleChange<number>) => handle(!raw ? 0 : Number(raw))"
            label="Full Year Fee"
            name="fullYearFee"
            rules="required|minValue:0"
          />

          <VvField
            v-model="periodForm.alumniFee"
            :component-props="{ type: 'number', step: '0.01', inputmode: 'decimal', 'data-testid': 'contribution-period-alumni-fee-field' }"
            :update="(raw: string, handle: HandleChange<number>) => handle(raw === '' ? 0 : Number(raw))"
            label="Alumni Fee"
            name="alumniFee"
            rules="required|minValue:0"
          />
        </Form>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          v-if="contributionPeriod?.id"
          color="red"
          data-testid="contribution-period-delete-btn"
          @click="confirmDeletePeriod"
        >
          Delete
        </v-btn>
        <v-btn
          color="primary"
          :data-submit-mode="contributionPeriod?.id ? 'update' : 'create'"
          data-testid="contribution-period-submit-btn"
          @click="saveContributionPeriod"
        >
          {{ contributionPeriod?.id ? "Save" : "Create" }}
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
import {computed, reactive, ref, watch} from "vue"
import {Form, type FormContext} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import {
  type ContributionPeriodResponse,
  createContributionPeriod,
  type CreateContributionPeriodRequest,
  updateContributionPeriod,
  type UpdateContributionPeriodRequest,
} from "@/services/api"
import {handleSubmitError} from "@/composables/formUtils"
import type {HandleChange} from "@/types/VVField.types.ts"

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

const periodForm = reactive<PeriodFormModel>(emptyPeriod())
const formRef = ref<FormContext>()

watch(
  () => props.contributionPeriod,
  (val) => {
    Object.assign(periodForm, val ?? emptyPeriod())
    formRef.value?.resetForm({values: {...periodForm}})
  },
  {immediate: true},
)

watch(
  () => props.showDialog,
  (open) => {
    if (open && !props.contributionPeriod) {
      Object.assign(periodForm, emptyPeriod())
      formRef.value?.resetForm({values: {...periodForm}})
    }
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
  if (periodForm.id != null) emit("delete", periodForm.id)
}

const saveContributionPeriod = async () => {
  const result = await formRef.value?.validate()
  if (!result?.valid) return

  try {
    if (periodForm?.id) {
      const payload: UpdateContributionPeriodRequest = {
        startDate: periodForm.startDate,
        endDate: periodForm.endDate,
        halfYearCutoffDate: periodForm.halfYearCutoffDate,
        halfYearFee: periodForm.halfYearFee,
        fullYearFee: periodForm.fullYearFee,
        alumniFee: periodForm.alumniFee,
        contactListId: periodForm.contactListId,
        version: periodForm.version ?? 0,
      }
      const resp = await updateContributionPeriod({
        body: payload,
        path: {id: periodForm.id as number},
        throwOnError: true,
      })
      emit("changed", resp.data!)
      closeDialog()
    } else {
      const payload: CreateContributionPeriodRequest = {
        startDate: periodForm.startDate,
        endDate: periodForm.endDate,
        halfYearCutoffDate: periodForm.halfYearCutoffDate,
        halfYearFee: periodForm.halfYearFee,
        fullYearFee: periodForm.fullYearFee,
        alumniFee: periodForm.alumniFee,
        contactListId: periodForm.contactListId,
      }
      const resp = await createContributionPeriod({body: payload, throwOnError: true})
      emit("changed", resp.data!)
      closeDialog()
    }
  } catch (err) {
    handleSubmitError(formRef.value, err)
  }
}
</script>
