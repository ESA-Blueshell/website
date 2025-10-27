<template>
  <v-dialog
    v-model="showDialog"
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
                name="startDate"
                label="Start Date"
                rules="required|dateBefore:@endDate"
                :component-props="{ type: 'date' }"
              />
            </v-col>
            <v-col cols="6">
              <VvField
                v-model="periodForm.endDate"
                name="endDate"
                label="End Date"
                rules="required|dateAfter:@startDate"
                :component-props="{ type: 'date' }"
              />
            </v-col>
          </v-row>

          <VvField
            v-model="periodForm.halfYearFee"
            name="halfYearFee"
            label="Half Year Fee"
            rules="required|minValue:0"
            :component-props="{ type: 'number', step: '0.01', inputmode: 'decimal' }"
            :update="(raw: string, handle: HandleChange<number>) => handle(!raw ? 0 : Number(raw))"
          />

          <VvField
            v-model="periodForm.fullYearFee"
            name="fullYearFee"
            label="Full Year Fee"
            rules="required|minValue:0"
            :component-props="{ type: 'number', step: '0.01', inputmode: 'decimal' }"
            :update="(raw: string, hande: HandleChange<number>) => handle(!raw ? 0 : Number(raw))"
          />

          <VvField
            v-model="periodForm.alumniFee"
            name="alumniFee"
            label="Alumni Fee"
            rules="required|minValue:0"
            :component-props="{ type: 'number', step: '0.01', inputmode: 'decimal' }"
            :update="(raw: string, hande: HandleChange<number>) => handle(raw === '' ? '' : Number(raw))"
          />
        </Form>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          v-if="contributionPeriod?.id"
          color="red"
          @click="confirmDeletePeriod"
        >
          Delete
        </v-btn>
        <v-btn
          color="primary"
          @click="saveContributionPeriod"
        >
          {{ contributionPeriod?.id ? "Save" : "Create" }}
        </v-btn>
        <v-btn @click="closeDialog">
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
import {type ContributionPeriod, createContributionPeriod, updateContributionPeriod} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {apply} from "@/plugins/validation.ts"
import type {HandleChange} from "@/types/VVField.types.ts"

defineOptions({name: "ContributionPeriodDialog"})

const props = defineProps<{ contributionPeriod?: ContributionPeriod; showDialog: boolean }>()
const emit = defineEmits<{
  (e: "update:showDialog", value: boolean): void;
  (e: "changed", value: ContributionPeriod): void;
  (e: "delete", value: number): void;
}>()

const emptyPeriod = (): ContributionPeriod => ({
  startDate: "",
  endDate: "",
  halfYearFee: 0,
  fullYearFee: 0,
  alumniFee: 0,
})

const periodForm = reactive<ContributionPeriod>(emptyPeriod())
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
      const resp = await updateContributionPeriod({
        body: periodForm,
        path: {id: periodForm.id as number},
        throwOnError: true,
      })
      emit("changed", resp.data!)
      closeDialog()
    } else {
      const resp = await createContributionPeriod({body: periodForm, throwOnError: true})
      emit("changed", resp.data!)
      closeDialog()
    }
  } catch (err) {
    if (!apply(formRef.value!, err)) $handleNetworkError(err)
  }
}
</script>
