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
      The undersigned hereby declares to be a member of Blueshell E-Sports Association Enschede until further notice.
      He/she hereby agrees to the Statutes, privacy policy and the Domestic Regulations (Huishoudelijk reglement) of
      this association. Cancellation must take place no later than four weeks before the beginning of the new academic
      year.
      <br><br>
      <document-table />
      <br>
      <contribution-period
        v-model="localMembership.memberType"
        is-form
      />

      <v-row
        class="mt-4"
        style="width: 100%"
      >
        <Field
          v-slot="{ errors, handleChange }"
          :rules="'required'"
          :validate-on-blur="false"
          :validate-on-change="false"
          name="signature"
        >
          <v-input
            :error-messages="errors"
            hide-details="auto"
          >
            <v-row class="d-flex justify-center mb-1">
              <VueSignaturePad
                ref="signaturePad"
                :options="{ backgroundColor: 'rgba(255,255,255)' }"
                :scale-to-device-pixel-ratio="true"
                :width="'100%'"
                style="aspect-ratio: 5/3"
                @end="() => onSignatureEnd(handleChange)"
              />
            </v-row>
          </v-input>
        </Field>
      </v-row>

      <v-row class="d-flex justify-end mt-4">
        <v-btn
          class="btn btn-danger"
          type="button"
          @click="clearSignature()"
        >
          Clear
        </v-btn>
      </v-row>

      <v-row>
        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="localMembership.city"
            name="city"
            rules="required|minChars:2"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Place"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
        <v-col cols="6">
          <!-- Signing date is informational for the user; we still validate it exists -->
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="signDate"
            name="date"
            rules="required"
          >
            <v-text-field
              :disabled="true"
              :error-messages="errors"
              :model-value="value"
              label="Date"
              type="date"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>
    </v-sheet>
  </Form>
</template>

<script lang="ts" setup>
import {onMounted, ref, type Ref, watch} from "vue"
import {DateTime} from "luxon"
import DocumentTable from "@/components/base/DocumentTable.vue"
import ContributionPeriod from "@/components/base/ContributionPeriodComponent.vue"
import VueSignaturePad, {type VueSignaturePadInstance} from "vue-signature-pad"
import {Field, Form, type FormContext, useForm} from "vee-validate"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {createMembership, updateMembership, uploadSignature, type File, type Membership} from "@/services/api"

interface Props {
  modelValue: Membership
}

type Emits = (e: "update:modelValue", value: Membership) => void

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const localMembership: Ref<Membership> = ref({...props.modelValue})
const signaturePad = ref<VueSignaturePadInstance>()
const signatureFile = ref<File | null>(null)
const signDate = ref<string>("")

watch(
  () => props.modelValue,
  (val) => {
    if (JSON.stringify(val) !== JSON.stringify(localMembership.value)) localMembership.value = {...val}
  },
  {deep: true, immediate: true},
)
watch(localMembership, (val) => emit("update:modelValue", val), {deep: true})

const formRef = ref<FormContext>()
const {setFieldValue, resetForm} = useForm()
const {apply} = useBackendValidation()

const validate = async (): Promise<boolean> => {
  const res = await formRef.value?.validate()
  return !!res?.valid
}

function getSignatureDataUrl(): { isEmpty: boolean; data?: string } {
  if (!signaturePad.value) return {isEmpty: true}
  return {isEmpty: signaturePad.value.isEmpty(), data: signaturePad.value.save("image/png")}
}

async function dataUrlToScaledBlob(dataUrl: string, width = 500, height = 300, type = "image/png"): Promise<Blob> {
  const img = await new Promise<HTMLImageElement>((resolve) => {
    const el = new Image()
    el.onload = () => resolve(el)
    el.src = dataUrl
  })
  const canvas = document.createElement("canvas")
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext("2d")!
  ctx.drawImage(img, 0, 0, width, height)
  return await new Promise<Blob>((resolve) => canvas.toBlob((b) => resolve(b!), type, 0.92))
}

async function onSignatureEnd(handleChange: (f: File | null) => void) {
  const {isEmpty, data} = getSignatureDataUrl()
  if (isEmpty || !data) {
    signatureFile.value = null
    handleChange(null)
    return
  }
  const blob = await dataUrlToScaledBlob(data)
  const file = new File([blob], `signature-${Date.now()}.png`, {type: "image/png"})
  signatureFile.value = file as unknown as File
  handleChange(signatureFile.value as File)
}

function clearSignature() {
  signaturePad.value?.clear()
  signatureFile.value = null
  setFieldValue("signature", null)
}

async function save(): Promise<void> {
  if (!(await validate())) throw new Error("Membership validation failed")
  try {
    if (signatureFile.value) {
      const resp = await uploadSignature({body: {file: (signatureFile.value as unknown) as Blob}})
      if (resp.status !== 201 && !apply(formRef.value!, resp)) $handleNetworkError(resp)
    }

    const resp = localMembership.value.id
      ? await updateMembership({path: {id: localMembership.value.id}, body: localMembership.value, throwOnError: true})
      : await createMembership({body: localMembership.value, throwOnError: true})

    if (resp?.data) {
      localMembership.value = resp.data
      emit("update:modelValue", resp.data)
      resetForm({values: {...resp.data, date: signDate.value, signature: null}})
    }
  } catch (err: unknown) {
    if (!apply(formRef.value!, err)) $handleNetworkError(err)
    throw err
  }
}

onMounted(() => {
  signDate.value = DateTime.now().toISODate()
})

defineExpose({save, validate, clearSignature})
</script>

<style lang="scss" scoped>
.v-sheet {
  background: white;
}
</style>
