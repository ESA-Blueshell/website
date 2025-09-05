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
    <v-row
      class="mt-4"
      style="width: 100%;"
    >
      <v-input
        ref="signature"
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
          ref="city"
          v-model="localMembership.city"
          label="Place"
          :rules="cityRules"
        />
      </v-col>
      <v-col cols="6">
        <v-text-field
          ref="date"
          v-model="localMembership.date"
          type="date"
          label="Date"
          :rules="dateRules"
          :disabled="true"
        />
      </v-col>
    </v-row>
  </v-sheet>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, type Ref } from 'vue';
import { DateTime } from 'luxon';
import DocumentTable from "@/components/DocumentTable.vue";
import ContributionPeriod from "@/components/ContributionPeriodComponent.vue";
import type { MembershipDto, FileDto } from "@/lib/types.gen";
import client from "@/plugins/client";
import { updateMembership, createMembership } from "@/lib/sdk.gen";

// Props interface
interface Props {
  modelValue: MembershipDto;
}

// Emits interface
interface Emits {
  (e: 'update:modelValue', value: MembershipDto): void;
}

// Define props and emits
const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Template refs
const signature: Ref<FileDto> = ref({});
const signaturePad: Ref = ref(null);
const city: Ref<string> = ref('');
const date: Ref<string> = ref('');

// Reactive data
const localMembership: Ref<MembershipDto> = ref({ ...props.modelValue });

// Validation rules
const signatureRules: Ref<Array<(v: any) => boolean | string>> = ref([
  (v: any) => !!v || 'Signature is required',
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
  (newVal: MembershipDto) => {
    localMembership.value = { ...newVal };
  },
  { deep: true, immediate: true }
);

// Watch for local changes and emit
watch(
  localMembership,
  (newVal: MembershipDto) => {
    emit('update:modelValue', newVal);
  },
  { deep: true }
);

// Methods
const clearSignature = (): void => {
  if (signaturePad.value) {
    signaturePad.value.clearSignature();
    localMembership.value.signature = undefined;
  }
};

const saveSignature = async (): Promise<void> => {
  if (!signaturePad.value) return;

  const { isEmpty, data }: { isEmpty: boolean; data: string } = signaturePad.value.saveSignature('image/png');

  if (isEmpty) {
    localMembership.value.signature = undefined;
  } else {
    const scaledData: string = await scaleSignature(data);
    localMembership.value.signature = {
      base64Content: scaledData.split(',')[1],
      fileType: 'SIGNATURE'
    } as FileDto;
  }

  emit('update:modelValue', localMembership.value);
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
        resolve(canvas.toDataURL());
      }
    };
  });
};

const saveMembership = async (): Promise<void> => {
  try {
    let response: { data?: MembershipDto };

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
    }
  } catch (error: unknown) {
    console.error('Failed to save membership:', error);
  }
};

// Lifecycle hooks
onMounted(() => {
  if (!localMembership.value.date) {
    localMembership.value.date = DateTime.now().toISODate();
  }
});

// Expose methods that might be called from parent components
defineExpose({
  saveSignature,
  saveMembership,
  clearSignature
});
</script>
