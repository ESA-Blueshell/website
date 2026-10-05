<script lang="ts" setup>
import {ref} from "vue"
import DocumentTable from "@/components/base/DocumentTable.vue"
import ContributionPeriod from "@/components/base/ContributionPeriodComponent.vue"
import {defineRule, Form} from "vee-validate"
import {applyForMembership, type SignupOutcomeResponse, startOwnMembership} from "@/domains/user"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox} from "vuetify/components"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {handleSubmitError, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"
import type {FieldMap} from "@/plugins/validation"

// TWIN: the api's MembershipConditions, which refuses an application that does not accept them.
defineRule("accepted", (value: unknown) => value === true || "You must accept the membership conditions to continue.")

// The request calls the agreement `conditionsAccepted` and the checkbox that
// collects it is `consented`, so a refusal only reaches the box by name.
const membershipFieldMap: FieldMap = {
  conditionsAccepted: "consented",
}

const props = withDefaults(defineProps<{
  showSubmit?: boolean
  submitText?: string
  /** data-testid forwarded to the SubmitButton (for testid preservation across consumers) */
  submitTestId?: string
  /** Present during a signup: the application is submitted on the token's account. */
  signupToken?: string
}>(), {
  showSubmit: false,
  submitText: "Submit",
  submitTestId: "membership-form-submit-btn",
  signupToken: undefined,
})

const emit = defineEmits<{ (e: "submitted", ok: boolean): void }>()

const {formRef, validate} = useVeeForm()
const {isSaving, withSaving} = useSaving()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const consented = ref(false)

const save = async (): Promise<SignupOutcomeResponse | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
  try {
    // A new applicant submits on their signup token; both routes answer with the
    // outcome rather than a membership, because the application may be complete
    // without the membership having started yet.
    if (props.signupToken) {
      const outcome = await withSaving(async () =>
        await applyForMembership(props.signupToken!, consented.value))
      emit("submitted", true)
      setSubmitResult(true)
      return outcome
    }
    const outcome = await withSaving(async () => await startOwnMembership(consented.value))
    emit("submitted", true)
    setSubmitResult(true)
    return outcome
  } catch (err: unknown) {
    handleSubmitError(formRef.value, err, membershipFieldMap)
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
    <!-- The membership conditions and the box that accepts them. -->
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

      <contribution-period is-form />

      <div class="checkbox-row">
        <VvField
          v-model="consented"
          :component="VCheckbox"
          :component-props="{ hideDetails: 'auto', class: 'w-100' }"
          label="I confirm that I have read and agree to the membership terms above, including the Statutes, Domestic Regulations, and Privacy Policy, and I understand these conditions are required for membership."
          name="consented"
          rules="accepted"
        />
      </div>
    </v-sheet>

    <v-row
      align="end"
      class="mb-5 mt-2"
      justify="end"
    >
      <v-col
        v-if="props.showSubmit"
        cols="auto"
      >
        <submit-button
          :disabled="isSaving || !meta.valid"
          icon="mdi-content-save"
          :loading="isSaving"
          :show-submit-status="showSubmitStatus"
          :submit-state="submitState"
          :text="props.submitText"
          :data-testid="props.submitTestId"
          data-submit-mode="create"
          @click="save"
        />
      </v-col>
    </v-row>
  </Form>
</template>

<style lang="scss" scoped>
.checkbox-row {
  width: 100%;
}

.checkbox-row :deep(.v-selection-control) {
  align-items: flex-start;
}

.checkbox-row :deep(.v-label) {
  white-space: normal;
  text-wrap: pretty;
}
</style>
