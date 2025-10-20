<template>
  <Form
    ref="formRef"
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
        align="end"
        class="mb-5 mt-2"
        justify="end"
      >
        <v-col
          v-if="showSubmit"
          cols="auto"
        >
          <v-btn
            :disabled="isSaving"
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
import {Form, type FormContext} from "vee-validate"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {createMembership, type Membership, updateMembership} from "@/services/api"

const {showSubmit = false, submitText = "Submit"} = defineProps<{
  showSubmit?: boolean
  submitText?: string
}>()

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

const membership = defineModel<Membership>({
  default: () => ({}) as Membership,
})

const formRef = ref<FormContext>()
const {apply} = useBackendValidation()

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
        body: membership.value!,
        throwOnError: true,
      })

    if (resp?.data) {
      membership.value = resp.data
    }

    emit("submitted", true)
    return resp?.data ?? null
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
