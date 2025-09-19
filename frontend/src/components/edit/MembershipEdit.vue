<template>
  <v-sheet
    class="pa-4"
    style="border-radius: 10px"
  >
    <strong>Membership conditions</strong><br>
    The undersigned hereby declares to be a member of Blueshell E-Sports Association Enschede until further
    notice. He/she hereby agrees to the Statutes, privacy policy and the Domestic Regulations (Huishoudelijk
    reglement) of this association. Cancellation must take place no later than four weeks before the beginning of
    the new academic year.
    <br><br>
    <document-table />
    <br>
    <contribution-period
      v-model="localMembership.memberType"
      is-form
    />
    <v-form ref="membershipForm">
      <v-row
        class="mt-4"
        style="width: 100%;"
      >
        <v-input
          ref="signatureInput"
          :rules="signatureRules"
          hide-details="auto"
        >
          <v-row class="d-flex justify-center mb-1">
            <VueSignaturePad
              ref="signaturePad"
              style="aspect-ratio: 5/3"
              :width="'100%'"
              :options="{backgroundColor: 'rgba(255,255,255)'}"
              :scale-to-device-pixel-ratio="true"
              @end="onSignatureEnd"
            />
          </v-row>
        </v-input>
      </v-row>
      <v-row class="d-flex justify-end mt-4">
        <v-btn
          type="button"
          class="btn btn-danger"
          @click="clearSignature"
        >
          Clear
        </v-btn>
      </v-row>
      <v-row>
        <v-col cols="6">
          <v-text-field
            ref="cityField"
            v-model="localMembership.city"
            label="Place"
            :rules="cityRules"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            ref="dateField"
            v-model="localMembership.date"
            type="date"
            label="Date"
            :rules="dateRules"
            :disabled="true"
          />
        </v-col>
      </v-row>
    </v-form>
  </v-sheet>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, type Ref } from 'vue';
import { DateTime } from 'luxon';
import DocumentTable from "@/components/DocumentTable.vue";
import ContributionPeriod from "@/components/ContributionPeriodComponent.vue";
import type { Membership, File } from "@/lib";
import { updateMembership, createMembership } from "@/lib";

// Props interface
interface Props {
  modelValue: Membership;
}

// Emits interface
interface Emits {
  (e: 'update:modelValue', value: Membership): void;
}

// Define props and emits
const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Template refs
const membershipForm: Ref<any> = ref(null);
const signatureInput: Ref<any> = ref(null);
const signaturePad: Ref<any> = ref(null);
const cityField: Ref<any> = ref(null);
const dateField: Ref<any> = ref(null);

// Reactive data
const localMembership: Ref<Membership> = ref({ ...props.modelValue });
const signatureValidation: Ref<boolean> = ref(false);

// Validation rules
const signatureRules: Ref<Array<(v: any) => boolean | string>> = ref([
  () => signatureValidation.value || 'Signature is required',
]);

const cityRules: Ref<Array<(v: any) => boolean | string>> = ref([
  (v: any) => !!v || 'Place is required',
]);

const dateRules: Ref<Array<(v: any) => boolean | string>> = ref([
  (v: any) => !!v || 'Date is required',
]);

// Watch for prop changes
watch(
  () => props.modelValue,
  (newVal: Membership) => {
    localMembership.value = { ...newVal };
    // Update signature validation state
    signatureValidation.value = !!(localMembership.value.signature?.base64Content || localMembership.value.signature?.url);
  },
  { deep: true, immediate: true }
);

// Watch for local changes and emit
watch(
  localMembership,
  (newVal: Membership) => {
    emit('update:modelValue', newVal);
  },
  { deep: true }
);

// Methods
const clearSignature = (): void => {
  if (signaturePad.value) {
    signaturePad.value.clearSignature();
    localMembership.value.signature = undefined;
    signatureValidation.value = false;

    // Trigger validation update
    if (signatureInput.value) {
      signatureInput.value.validate();
    }
  }
};

const onSignatureEnd = (): void => {
  // Reset validation state when user draws
  signatureValidation.value = false;
  if (signatureInput.value) {
    signatureInput.value.validate();
  }
};

const saveSignature = async (): Promise<boolean> => {
  if (!signaturePad.value) return false;

  const { isEmpty, data }: { isEmpty: boolean; data: string } = signaturePad.value.saveSignature('image/png');

  if (isEmpty) {
    localMembership.value.signature = undefined;
    signatureValidation.value = false;
    return false;
  }

  try {
    const scaledData: string = await scaleSignature(data);

    // Create a File with base64 content for the signature
    const signatureFile: File = {
      base64Content: scaledData.split(",")[1], // Remove data URL prefix
      fileType: 'SIGNATURE',
      mediaType: 'image/png'
    };

    // Store the signature in the membership
    localMembership.value.signature = signatureFile;
    signatureValidation.value = true;

    // Trigger validation update
    if (signatureInput.value) {
      signatureInput.value.validate();
    }

    emit('update:modelValue', localMembership.value);
    return true;
  } catch (error: unknown) {
    console.error('Failed to process signature:', error);
    signatureValidation.value = false;
    return false;
  }
};

const scaleSignature = (data: string): Promise<string> => {
  return new Promise((resolve) => {
    const image = new Image();
    image.src = data;
    image.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = 500;
      canvas.height = 300;
      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.drawImage(image, 0, 0, 500, 300);
        resolve(canvas.toDataURL('image/png'));
      }
    };
  });
};

const validateForm = async (): Promise<boolean> => {
  if (!membershipForm.value) return false;

  // First validate the form fields
  const { valid } = await membershipForm.value.validate();

  if (!valid) {
    return false;
  }

  // Check if signature needs to be saved
  if (signaturePad.value && !signatureValidation.value) {
    const { isEmpty }: { isEmpty: boolean } = signaturePad.value.saveSignature('image/png');

    if (!isEmpty && !localMembership.value.signature?.base64Content) {
      // Signature exists but hasn't been processed yet
      const signatureSaved = await saveSignature();
      if (!signatureSaved) {
        return false;
      }
    } else if (isEmpty) {
      // No signature provided
      signatureValidation.value = false;
      if (signatureInput.value) {
        signatureInput.value.validate();
      }
      return false;
    }
  }

  return true;
};

const saveMembership = async (): Promise<boolean> => {
  // Validate form before saving
  const isValid = await validateForm();

  if (!isValid) {
    console.error('Form validation failed');
    return false;
  }

  try {
    let response: { data?: Membership };

    if (localMembership.value.id) {
      // Update existing membership
      response = await updateMembership({
        path: { id: localMembership.value.id },
        body: localMembership.value,
        client
      });
    } else {
      // Create new membership
      response = await createMembership({
        body: localMembership.value,
        client
      });
    }

    if (response.data) {
      localMembership.value = response.data;
      emit('update:modelValue', response.data);
      return true;
    }

    return false;
  } catch (error: unknown) {
    console.error('Failed to save membership:', error);
    throw error;
  }
};

// Lifecycle hooks
onMounted(() => {
  if (!localMembership.value.date) {
    localMembership.value.date = DateTime.now().toISODate();
  }

  // Initialize signature validation state
  signatureValidation.value = !!(localMembership.value.signature?.base64Content || localMembership.value.signature?.url);
});

// Expose methods that might be called from parent components
defineExpose({
  saveSignature,
  saveMembership,
  clearSignature,
  validateForm
});
</script>

<style lang="scss" scoped>
.v-sheet {
  background: white;
}
</style>
