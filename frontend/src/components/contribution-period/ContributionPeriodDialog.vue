<template>
  <v-dialog
    v-model="showDialog"
    max-width="600"
  >
    <v-card>
      <v-card-title class="mt-6 align-center justify-center text-center">
        <span class="text-h4">{{
          contributionPeriod?.id ? "Edit Contribution Period" : "Add Contribution Period"
        }}</span>
      </v-card-title>
      <v-card-text>
        <Form
          as="div"
        >
          <v-row dense>
            <v-col cols="6">
              <Field
                v-slot="{ value, errors, handleChange, handleBlur }"
                v-model="periodForm.startDate"
                name="startDate"
                rules="required|dateBefore:@endDate"
              >
                <v-text-field
                  :error-messages="errors"
                  :model-value="value"
                  label="Start Date"
                  type="date"
                  @blur="handleBlur"
                  @update:model-value="handleChange"
                />
              </Field>
            </v-col>
            <v-col cols="6">
              <Field
                v-slot="{ value, errors, handleChange, handleBlur }"
                v-model="periodForm.endDate"
                name="endDate"
                rules="required|dateAfter:@startDate"
              >
                <v-text-field
                  :error-messages="errors"
                  :model-value="value"
                  label="End Date"
                  type="date"
                  @blur="handleBlur"
                  @update:model-value="handleChange"
                />
              </Field>
            </v-col>
          </v-row>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="periodForm.halfYearFee"
            name="halfYearFee"
            rules="required|minValue:0"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Half Year Fee"
              type="number"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="periodForm.fullYearFee"
            name="fullYearFee"
            rules="required|minValue:0"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Full Year Fee"
              type="number"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="periodForm.alumniFee"
            name="alumniFee"
            rules="required|minValue:0"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Alumni Fee"
              type="number"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
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
import {type ContributionPeriod, createContributionPeriod, updateContributionPeriod} from "@/lib"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {Field, Form, type FormContext} from "vee-validate"

defineOptions({name: "ContributionPeriodDialog"})
const {apply} = useBackendValidation()

const props = defineProps<{
  contributionPeriod?: ContributionPeriod,
  showDialog: boolean
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

const emit = defineEmits<{
  (e: "update:showDialog", value: boolean): void; // ← correct event
  (e: "changed", value: ContributionPeriod): void;
  (e: "delete", value: number): void;
}>()

const showDialog = computed({
  get: () => props.showDialog,
  set: (value: boolean) => emit("update:showDialog", value),
})


const closeDialog = () => {
  showDialog.value = false
}

const confirmDeletePeriod = () => {
  showDialog.value = false
  emit("delete", periodForm.id!)
}

const saveContributionPeriod = async () => {
  const result = await formRef.value?.validate()
  if (result?.valid) return

  if (periodForm?.id) {
    const resp = await updateContributionPeriod({
      body: periodForm,
      path: {id: periodForm.id as number},
    })

    if (resp.status === 200) {
      emit("changed", resp.data!)
      closeDialog()
    } else if (!apply(formRef.value!, resp)) {
      $handleNetworkError(resp)
    }
  } else {
    const resp = await createContributionPeriod({body: periodForm})

    if (resp.status === 201) {
      emit("changed", resp.data!)
      closeDialog()
    } else if (!apply(formRef.value!, resp)) {
      $handleNetworkError(resp)
    }
  }
}

</script>
