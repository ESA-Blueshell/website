<template>
  <v-dialog
    v-model="showDialog"
    max-width="500"
  >
    <v-card>
      <v-card-title class="mx-7 mt-6  align-center justify-center">
        <span class="text-h4">{{ isEditing ? 'Edit Contribution Period' : 'Add Contribution Period' }}</span>
      </v-card-title>
      <v-card-text>
        <v-form ref="formRef">
          <v-row dense>
            <v-col cols="6">
              <v-text-field
                v-model="form.startDate"
                label="Start Date"
                :rules="startDateRules"
                type="date"
              />
            </v-col>
            <v-col cols="6">
              <v-text-field
                v-model="form.endDate"
                label="End Date"
                :rules="endDateRules"
                type="date"
              />
            </v-col>
          </v-row>
          <v-text-field
            v-model.number="form.halfYearFee"
            label="Half Year Fee"
            type="number"
            :rules="feeRules"
          />
          <v-text-field
            v-model.number="form.fullYearFee"
            label="Full Year Fee"
            type="number"
            :rules="feeRules"
          />
          <v-text-field
            v-model.number="form.alumniFee"
            label="Alumni Fee"
            type="number"
            :rules="feeRules"
          />
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          v-if="isEditing"
          color="red"
          @click="confirmDeletePeriod"
        >
          Delete
        </v-btn>
        <v-btn
          color="primary"
          @click="saveContributionPeriod"
        >
          {{ isEditing ? 'Save' : 'Create' }}
        </v-btn>
        <v-btn @click="closeDialog">
          Cancel
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, toRef } from 'vue';
import { DateTime } from 'luxon';
import type { VForm } from 'vuetify/components';
import { type ContributionPeriodDto, createContributionPeriod, updateContributionPeriod } from '@/lib';
import { $handleNetworkError } from '@/plugins/handleNetworkError.ts';

defineOptions({ name: 'ContributionPeriodDialog' });

type Props = {
  modelValue: boolean;
  isEditing: boolean;
  selectedPeriod: ContributionPeriodDto | null;
  contributionPeriods: ContributionPeriodDto[];
};

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'refresh-periods'): void;
  (e: 'delete'): void;
}>();

const showDialog = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const formRef = ref<VForm | null>(null);

const form = reactive<ContributionPeriodDto>({
  startDate: '',
  endDate: '',
  halfYearFee: 0,
  fullYearFee: 0,
  alumniFee: 0,
} as ContributionPeriodDto);

const contributionPeriods = toRef(props, 'contributionPeriods');
const selectedPeriod = toRef(props, 'selectedPeriod');

const feeRules = [
  (value: number | null) => (value !== null && value !== 0) || 'Fee is required',
];

const startsAfterLatest = (startDate: string): boolean => {
  if (!contributionPeriods.value || contributionPeriods.value.length === 0) return true;

  // Exclude the current period when editing
  const otherPeriods = contributionPeriods.value.filter(
    (p) => p.id !== selectedPeriod.value?.id
  );

  if (otherPeriods.length === 0) return true;

  const latestEndDate = otherPeriods.reduce((latest, period) => {
    const end = DateTime.fromISO(period.endDate);
    return end > latest ? end : latest;
  }, DateTime.fromISO(otherPeriods[0].endDate));

  return DateTime.fromISO(startDate) >= latestEndDate;
};

const isValidStartDate = (startDate: string): boolean => {
  if (!form.endDate) return true;
  return DateTime.fromISO(startDate) < DateTime.fromISO(form.endDate);
};

const isValidEndDate = (endDate: string): boolean => {
  if (!form.startDate) return true;
  return DateTime.fromISO(endDate) > DateTime.fromISO(form.startDate);
};

const startDateRules = computed(() => [
  (value: string) => !!value || 'Start Date is required',
  (value: string) => isValidStartDate(value) || 'Start date must be before end date',
  (value: string) =>
    startsAfterLatest(value) ||
    'The period must start after the latest contribution period',
]);

const endDateRules = computed(() => [
  (value: string) => !!value || 'End Date is required',
  (value: string) => isValidEndDate(value) || 'End date must be after start date',
]);

const closeDialog = () => {
  emit('update:modelValue', false);
};

const confirmDeletePeriod = () => {
  emit('update:modelValue', false);
  emit('delete');
};

const saveContributionPeriod = async () => {
  const result = await formRef.value?.validate();
  if (!result?.valid) return;

  try {
    if (props.isEditing) {
      await updateContributionPeriod({
        body: form,
        path: { id: form.id as number },
      });
    } else {
      await createContributionPeriod({ body: form });
    }
    emit('refresh-periods');
    closeDialog();
  } catch (e) {
    $handleNetworkError(e);
  }
};

// Populate/reset when dialog opens
watch(
  () => showDialog.value,
  (open) => {
    if (!open) return;
    if (props.isEditing && props.selectedPeriod) {
      Object.assign(form, props.selectedPeriod);
    }
  }
);
</script>

<style lang="scss">
</style>
