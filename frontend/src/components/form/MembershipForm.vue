<template>
  <Form
    ref="formRef"
    v-slot="{ meta }"
    as="div"
  >
    <v-sheet
      class="pa-4"
      style="border-radius: 10px"
    >
      <strong>Membership conditions</strong><br>
      By submitting this form you declare to be a member of Blueshell E-Sports Association Enschede until further
      notice.
      You hereby agree to the Statutes, privacy policy and the Domestic Regulations (Huishoudelijk reglement) of
      this association. Cancellation must take place no later than four weeks before the beginning of the new academic
      year.

      <br><br>
      <document-table />
      <br>

      <contribution-period
        v-model="membership.memberType"
        is-form
      />

      <v-row
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <VvField
            v-model="consented"
            :component="VCheckbox"
            :component-props="{ hideDetails: true }"
            name="consented"
            label="I have understood and agree to the terms and conditions for membership listed above."
            rules="accepted"
          />
        </v-col>
      </v-row>

      <v-row
        align="end"
        class="mb-5 mt-2"
        justify="end"
      >
        <v-col
          v-if="showSubmit"
          cols="auto"
        >
          <v-btn
            :disabled="isSaving || !meta.valid"
            :loading="isSaving"
            :prepend-icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            size="large"
            type="button"
            @click="save"
          >
            {{ submitText }}
          </v-btn>
        </v-col>
      </v-row>
    </v-sheet>
  </Form>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import DocumentTable from "@/components/base/DocumentTable.vue"
import ContributionPeriod from "@/components/base/ContributionPeriodComponent.vue"
import {defineRule, Form, type FormContext} from "vee-validate"
import {apply} from "@/plugins/validation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {createMembership, type Membership, updateMembership} from "@/services/api"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox} from "vuetify/components"

defineRule("accepted", (value: unknown) => {
  return value === true || "You must accept the membership conditions to continue."
})

const {showSubmit = false, submitText = "Submit"} = defineProps<{
  showSubmit?: boolean
  submitText?: string
}>()

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
  (e: "update:modelValue", value: Membership): void
}>()

const membership = defineModel<Membership>({default: () => ({}) as Membership})

const formRef = ref<FormContext>()
const consented = ref(false)

const isSaving = ref(false)
const isCreating = computed<boolean>(() => !membership.value?.id)

const validate = async (): Promise<boolean> => {
  const res = await formRef.value?.validate()
  return !!res?.valid
}

const save = async (): Promise<Membership | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    return null
  }

  isSaving.value = true
  try {
    const resp = membership.value?.id
      ? await updateMembership({
        path: {id: membership.value.id},
        body: membership.value!,
        throwOnError: true,
      })
      : await createMembership({
        throwOnError: true,
      })

    membership.value = resp.data!
    emit("submitted", true)
    emit("update:modelValue", membership.value)
    return membership.value
  } catch (err: unknown) {
    if (!formRef.value || !apply(formRef.value, err)) {
      $handleNetworkError(err)
    }
    emit("submitted", false)
    return null
  } finally {
    isSaving.value = false
  }
}

defineExpose({validate, save})
</script>
