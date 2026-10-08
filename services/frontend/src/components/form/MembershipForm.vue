<script lang="ts" setup>
import {computed, reactive, ref} from "vue"
import DocumentTable from "@/components/base/DocumentTable.vue"
import ContributionPeriod from "@/components/base/ContributionPeriodComponent.vue"
import {
  applyForMembership,
  type MembershipResponse,
  saveMembership,
  type SignupOutcomeResponse,
  startMembershipAsBoard,
  startOwnMembership,
} from "@/domains/user"
import FormControl from "@/components/island/FormControl.vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {useSaving} from "@/composables/formUtils"
import {type FieldChecks, type FieldMap, reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {accepted, required} from "@/utils/checks"

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

// A default handed to an unbound v-model stays raw, so its checks would never see an edit.
const membership = defineModel<MembershipResponse>({default: () => reactive({}) as MembershipResponse})

const {isSaving, withSaving} = useSaving()
const consented = ref(false)
const isCreating = computed<boolean>(() => !membership.value?.id)
const isBoardMode = computed<boolean>(() => props.userId !== undefined)

const checks = useFormChecks((): Record<string, FieldChecks> => (isBoardMode.value
  ? {
    startDate: {value: () => membership.value.startDate, checks: [required]},
    endDate: {value: () => membership.value.endDate, checks: []},
    memberType: {value: () => membership.value.memberType, checks: [required]},
  }
  : {
    // TWIN: the api's MembershipConditions, which refuses an application that does not accept them.
    consented: {
      value: () => consented.value,
      checks: [accepted("You must accept the membership conditions to continue.")],
    },
  }))
const {errorsOf, touch, valid} = checks
const validate = async (): Promise<boolean> => checks.attempt()

const save = async (): Promise<MembershipResponse | SignupOutcomeResponse | null> => {
  if (!(await validate())) {
    emit("submitted", false)
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
      return outcome
    }
    if (!membership.value?.id && props.userId === undefined) {
      const outcome = await withSaving(async () => await startOwnMembership(consented.value))
      emit("submitted", true)
      return outcome
    }
    const resp = await withSaving(async () => {
      if (membership.value?.id) {
        // Updating an existing membership — board or self-service both use updateMembership
        return await saveMembership(membership.value.id, membership.value!)
      }
      // Board creating a membership for a target user
      return await startMembershipAsBoard(props.userId!, membership.value!)
    })
    membership.value = resp
    emit("submitted", true)
    return membership.value
  } catch (err: unknown) {
    reportRefusal(checks, err, membershipFieldMap)
    emit("submitted", false)
    return null
  }
}

defineExpose({validate, save})
</script>

<template>
  <div>
    <!-- Board mode: compact date/type/incasso fields for administrative use -->
    <template v-if="isBoardMode">
      <v-row dense>
        <v-col
          cols="12"
          sm="6"
        >
          <form-control
            v-model="membership.startDate"
            data-testid="membership-form-start-date"
            :error-messages="errorsOf('startDate')"
            kind="date"
            label="Start Date"
            @blur="touch('startDate')"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
        >
          <form-control
            v-model="membership.endDate"
            data-testid="membership-form-end-date"
            :error-messages="errorsOf('endDate')"
            kind="date"
            label="End Date"
            @blur="touch('endDate')"
          />
        </v-col>
      </v-row>
      <v-row dense>
        <v-col
          cols="12"
          sm="6"
        >
          <member-type-select
            v-model="membership.memberType"
            data-testid="membership-form-member-type"
            :error-messages="errorsOf('memberType')"
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
      <div class="membership-terms">
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
          <check-box
            v-model="consented"
            :error-messages="errorsOf('consented')"
            label="I confirm that I have read and agree to the membership terms above, including the Statutes, Domestic Regulations, and Privacy Policy, and I understand these conditions are required for membership."
          />
        </div>
      </div>
    </template>

    <div
      v-if="props.showSubmit"
      class="form-save"
    >
      <submit-button
        :disabled="isSaving || !valid"
        :loading="isSaving"
        :text="props.submitText"
        :data-testid="props.submitTestId"
        :data-submit-mode="isCreating ? 'create' : 'update'"
        @click="save"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.membership-terms {
  padding: 1rem 1.1rem;
  background-color: var(--band-ground);
}

.form-save {
  display: flex;
  justify-content: flex-end;
  margin: 1.2rem 0 1.25rem;
}

.checkbox-row {
  width: 100%;
}

.checkbox-row :deep(.v-label) {
  white-space: normal;
  text-wrap: pretty;
}
</style>
