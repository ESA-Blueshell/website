<script lang="ts" setup>
import {computed, ref} from "vue"
import DocumentTable from "@/components/base/DocumentTable.vue"
import ContributionPeriod from "@/components/base/ContributionPeriodComponent.vue"
import {defineRule, Form} from "vee-validate"
import {
  apply,
  boardCreateMembership,
  createMembership,
  type MembershipResponse,
  type SignupOutcomeResponse,
  updateMembership,
} from "@/services/api"
import VvField from "@/components/form/fields/VvField.vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import {VCheckbox} from "vuetify/components"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {handleSubmitError, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"
import type {FieldMap} from "@/plugins/validation"

defineRule("accepted", (value: unknown) => value === true || "You must accept the membership conditions to continue.")

// The request calls the agreement `conditionsAccepted` and the checkbox that
// collects it is `consented`, so a refusal only reaches the box by name.
const membershipFieldMap: FieldMap = {
  conditionsAccepted: "consented",
}

const props = withDefaults(defineProps<{
  showSubmit?: boolean
  submitText?: string
  /**
   * When provided, save() uses boardCreateMembership (create) / updateMembership (update)
   * for managing another user's memberships as board. Without it the self-service
   * createMembership is used.
   */
  userId?: number
  /** data-testid forwarded to the SubmitButton (for testid preservation across consumers) */
  submitTestId?: string
  /** Present during a signup: the application is submitted on the token's account. */
  signupToken?: string
}>(), {
  showSubmit: false,
  submitText: "Submit",
  userId: undefined,
  submitTestId: "membership-form-submit-btn",
  signupToken: undefined,
})

const emit = defineEmits<{ (e: "submitted", ok: boolean): void }>()

const membership = defineModel<MembershipResponse>({default: () => ({}) as MembershipResponse})

const {formRef, validate} = useVeeForm()
const {isSaving, withSaving} = useSaving()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const consented = ref(false)
const isCreating = computed<boolean>(() => !membership.value?.id)
const isBoardMode = computed<boolean>(() => props.userId !== undefined)

const save = async (): Promise<MembershipResponse | SignupOutcomeResponse | null> => {
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
      const resp = await withSaving(async () => await apply({
        headers: {"X-Signup-Token": props.signupToken!},
        body: {conditionsAccepted: consented.value},
        throwOnError: true,
      }))
      emit("submitted", true)
      setSubmitResult(true)
      return resp.data!
    }
    if (!membership.value?.id && props.userId === undefined) {
      const resp = await withSaving(async () => await createMembership({
        body: {conditionsAccepted: consented.value},
        throwOnError: true,
      }))
      emit("submitted", true)
      setSubmitResult(true)
      return resp.data!
    }
    const resp = await withSaving(async () => {
      if (membership.value?.id) {
        // Updating an existing membership — board or self-service both use updateMembership
        return await updateMembership({path: {id: membership.value.id}, body: membership.value!, throwOnError: true})
      }
      // Board creating a membership for a target user
      return await boardCreateMembership({path: {userId: props.userId!}, body: membership.value!, throwOnError: true})
    })
    membership.value = resp.data!
    emit("submitted", true)
    setSubmitResult(true)
    return membership.value
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
    <!-- Board mode: compact date/type/incasso fields for administrative use -->
    <template v-if="isBoardMode">
      <v-row dense>
        <v-col
          cols="12"
          sm="6"
        >
          <VvField
            v-model="membership.startDate"
            :component-props="{ type: 'date', 'data-testid': 'membership-form-start-date' }"
            label="Start Date"
            name="startDate"
            rules="required"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
        >
          <VvField
            v-model="membership.endDate"
            :component-props="{ type: 'date', 'data-testid': 'membership-form-end-date' }"
            label="End Date"
            name="endDate"
          />
        </v-col>
      </v-row>
      <v-row dense>
        <v-col
          cols="12"
          sm="6"
        >
          <VvField
            v-model="membership.memberType"
            :component="MemberTypeSelect"
            :component-props="{ 'data-testid': 'membership-form-member-type' }"
            label="Member Type"
            name="memberType"
            rules="required"
          />
        </v-col>
        <v-col
          class="d-flex align-center justify-center"
          cols="12"
          sm="6"
        >
          <v-checkbox
            v-model="membership.incasso"
            data-testid="membership-form-incasso"
            hide-details
            label="Incasso"
          />
        </v-col>
      </v-row>
    </template>

    <!-- Self-service mode: membership conditions + consent + member type -->
    <template v-else>
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
            :component-props="{ hideDetails: true, class: 'w-100' }"
            label="I confirm that I have read and agree to the membership terms above, including the Statutes, Domestic Regulations, and Privacy Policy, and I understand these conditions are required for membership."
            name="consented"
            rules="accepted"
          />
        </div>
      </v-sheet>
    </template>

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
          :icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
          :loading="isSaving"
          :show-submit-status="showSubmitStatus"
          :submit-state="submitState"
          :text="props.submitText"
          :data-testid="props.submitTestId"
          :data-submit-mode="isCreating ? 'create' : 'update'"
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
