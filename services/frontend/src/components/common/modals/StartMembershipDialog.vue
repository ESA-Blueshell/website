<template>
  <v-dialog
    v-model="open"
    data-testid="start-membership-dialog"
    max-width="500"
  >
    <v-card>
      <v-card-title class="text-h5">
        Start Membership
      </v-card-title>

      <v-card-text>
        <v-row>
          <v-col cols="12">
            <form-control
              v-model="membership.startDate"
              data-testid="start-membership-start-date-field"
              :error-messages="errorsOf('startDate')"
              kind="date"
              label="Start Date"
              :max="maxDate"
              @blur="touch('startDate')"
            />
          </v-col>
        </v-row>

        <v-row>
          <v-col cols="12">
            <member-type-select
              v-model="membership.memberType"
              data-testid="start-membership-member-type-field"
              :error-messages="errorsOf('memberType')"
            />
          </v-col>
        </v-row>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          :disabled="isSubmitting"
          color="secondary"
          data-testid="start-membership-cancel-btn"
          @click="open = false"
        >
          Cancel
        </v-btn>
        <v-btn
          :disabled="isSubmitting"
          :loading="isSubmitting"
          color="primary"
          data-testid="start-membership-confirm-btn"
          @click="confirm"
        >
          Confirm
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {DateTime} from "luxon"
import FormControl from "@/components/island/FormControl.vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import {
  type BoardCreateMembershipRequest,
  MemberType,
  type MembershipResponse,
  startMembershipAsBoard,
} from "@/domains/user"
import {reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {required} from "@/utils/checks"

interface Props {
  modelValue: boolean;
  userId: number;
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "update:membership", value: MembershipResponse): void;
}>()

const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
})

const maxDate = DateTime.now().toISODate()

const membership = ref<BoardCreateMembershipRequest>({
  startDate: maxDate,
  memberType: MemberType.REGULAR,
  userId: props.userId,
  incasso: false,
})

const checks = useFormChecks(() => ({
  startDate: {value: () => membership.value.startDate, checks: [required]},
  memberType: {value: () => membership.value.memberType, checks: [required]},
}))
const {errorsOf, touch} = checks

const isSubmitting = ref(false)

const confirm = async () => {
  if (!checks.attempt()) return

  isSubmitting.value = true
  try {
    emit("update:membership", await startMembershipAsBoard(props.userId, membership.value))
    open.value = false
  } catch (error) {
    reportRefusal(checks, error)
  } finally {
    isSubmitting.value = false
  }
}
</script>
