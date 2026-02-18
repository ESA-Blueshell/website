<template>
  <v-dialog
    v-model="open"
    max-width="500"
  >
    <v-card>
      <v-card-title class="text-h5">
        Start Membership
      </v-card-title>

      <v-card-text>
        <Form
          ref="formRef"
          as="div"
        >
          <v-row>
            <v-col cols="12">
              <VvField
                v-model="membership.startDate"
                :component-props="{ type: 'date', max: maxDate }"
                label="Start Date"
                name="startDate"
                rules="required"
              />
            </v-col>
          </v-row>

          <v-row>
            <v-col cols="12">
              <VvField
                v-model="membership.memberType"
                :component="MemberTypeSelect"
                label="Member Type"
                name="memberType"
                rules="required"
              />
            </v-col>
          </v-row>
        </Form>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          :disabled="isSubmitting"
          color="secondary"
          @click="open = false"
        >
          Cancel
        </v-btn>
        <v-btn
          :loading="isSubmitting"
          color="primary"
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
import {Form, type FormContext} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import {boardCreateMembership, type BoardCreateMembershipRequest, MemberType, type MembershipResponse} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {apply} from "@/plugins/validation.ts"

interface Props {
  modelValue: boolean;
  userId: number;
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "update:membership", value: MembershipResponse): void;
}>()

const formRef = ref<FormContext>()
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

const isSubmitting = ref(false)

const confirm = async () => {
  const validation = await formRef.value?.validate()
  if (!validation?.valid) return

  isSubmitting.value = true
  try {
    const response = await boardCreateMembership({
      path: {userId: props.userId},
      body: membership.value,
      throwOnError: true,
    })
    if (response.data) {
      emit("update:membership", response.data)
      open.value = false
    }
  } catch (error) {
    if (!apply(formRef.value!, error)) $handleNetworkError(error)
  } finally {
    isSubmitting.value = false
  }
}
</script>
