<script lang="ts" setup>
import {computed, ref} from "vue"
import DocumentTable from "@/components/base/DocumentTable.vue"
import ContributionPeriod from "@/components/base/ContributionPeriodComponent.vue"
import {defineRule, Form} from "vee-validate"
import {createMembership, type MembershipResponse, updateMembership} from "@/services/api"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox} from "vuetify/components"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {handleSubmitError, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"

defineRule("accepted", (value: unknown) => value === true || "You must accept the membership conditions to continue.")

const {showSubmit = false, submitText = "Submit"} = defineProps<{ showSubmit?: boolean; submitText?: string }>()
const emit = defineEmits<{ (e: "submitted", ok: boolean): void }>()

const membership = defineModel<MembershipResponse>({default: () => ({}) as MembershipResponse})

const {formRef, validate} = useVeeForm()
const {isSaving, withSaving} = useSaving()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const consented = ref(false)
const isCreating = computed<boolean>(() => !membership.value?.id)

const save = async (): Promise<MembershipResponse | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
  try {
    const resp = await withSaving(async () => {
      return membership.value?.id
        ? await updateMembership({path: {id: membership.value.id}, body: membership.value!, throwOnError: true})
        : await createMembership({throwOnError: true})
    })
    membership.value = resp.data!
    emit("submitted", true)
    setSubmitResult(true)
    return membership.value
  } catch (err: unknown) {
    handleSubmitError(formRef.value, err)
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
}

defineExpose({validate, save})
</script>

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
      notice. You hereby agree to the Statutes, privacy policy and the Domestic Regulations (Huishoudelijk reglement) of
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
            label="I have understood and agree to the terms and conditions for membership listed above."
            name="consented"
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
          <submit-button
            :disabled="isSaving || !meta.valid"
            :icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            :loading="isSaving"
            :show-submit-status="showSubmitStatus"
            :submit-state="submitState"
            :text="submitText"
            @click="save"
          />
        </v-col>
      </v-row>
    </v-sheet>
  </Form>
</template>
