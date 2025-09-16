<template>
  <v-dialog
    v-model="showDialog"
    max-width="500"
  >
    <v-card>
      <v-card-title>
        <span class="text-h5">{{ isEditing ? 'Edit ContributionModel Period' : 'Add ContributionModel Period' }}</span>
      </v-card-title>
      <v-card-text>
        <v-form ref="formRef">
          <v-row>
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

<script lang="ts">
import {
  ref,
  reactive,
  computed,
  defineComponent,
  toRefs,
  watch,
} from 'vue';
import {$handleNetworkError} from '@/plugins/handleNetworkError.ts';
import { DateTime } from 'luxon';
import type {ContributionPeriodModel} from '@/models';
import {ContributionPeriodService} from '@/services';
import type {VForm} from 'vuetify/components';

export default defineComponent({
  name: 'ContributionPeriodDialog',
  props: {
    modelValue: {
      type: Boolean,
      required: true,
    },
    isEditing: {
      type: Boolean,
      required: true,
    },
    selectedPeriod: {
      type: Object as () => ContributionPeriodModel | null,
      default: null,
    },
    contributionPeriods: {
      type: Array as () => ContributionPeriodModel[],
      default: () => [],
    },
  },
  emits: ['update:modelValue', 'refresh-periods', 'delete'],
  setup(props, {emit}) {
    const showDialog = computed({
      get: () => props.modelValue,
      set: (value) => emit('update:modelValue', value),
    });

    const form = reactive<ContributionPeriodModel>({
      id: props.selectedPeriod?.id || 0,
      startDate: '',
      endDate: '',
      halfYearFee: 0,
      fullYearFee: 0,
      alumniFee: 0,
    });

    const feeRules = [
      (value: number | null) => (value !== null && value !== 0) || 'Fee is required',
    ];
    const formRef = ref<VForm>();

    const {contributionPeriods, selectedPeriod} = toRefs(props);

    const contributionPeriodService = new ContributionPeriodService();

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

    const startsAfterLatest = (startDate: string): boolean => {
      if (!contributionPeriods.value || contributionPeriods.value.length === 0) {
        return true; // No periods to compare with
      }

      // Exclude the current period if editing
      const otherPeriods = contributionPeriods.value.filter(
        (period) => period.id !== selectedPeriod.value?.id
      );

      if (otherPeriods.length === 0) {
        return true;
      }

      // Find the latest end date among other periods
      const latestEndDate = otherPeriods.reduce((latest, period) => {
        const periodEndDate = DateTime.fromISO(period.endDate);
        return periodEndDate > latest ? periodEndDate : latest;
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

    const saveContributionPeriod = async () => {
      const valid = await formRef.value?.validate();

      if (!valid) {
        return;
      }

      try {
        if (props.isEditing) {
          await contributionPeriodService.updatePeriod(form.id as number, form);
        } else {
          await contributionPeriodService.createPeriod(form);
        }
        emit('refresh-periods');
        closeDialog();
      } catch (e) {
        $handleNetworkError(e);
      }
    };

    const closeDialog = () => {
      emit('update:modelValue', false);
      resetForm();
    };

    const resetForm = () => {
      form.id = 0;
      form.startDate = '';
      form.endDate = '';
      form.halfYearFee = 0;
      form.fullYearFee = 0;
      form.alumniFee = 0;
    };

    const confirmDeletePeriod = () => {
      emit('update:modelValue', false);
      emit('delete');
    };

    // Watch for changes in 'showDialog' to reset form when not editing
    watch(
      () => showDialog.value,
      (newVal) => {
        if (newVal) {
          if (props.isEditing && props.selectedPeriod) {
            // Populate form with selectedPeriod data when editing
            Object.assign(form, props.selectedPeriod);
          } else {
            // Reset form data when adding a new period
            resetForm();
          }
        }
      }
    );

    return {
      showDialog,
      form,
      feeRules,
      formRef,
      startDateRules,
      endDateRules,
      saveContributionPeriod,
      closeDialog,
      confirmDeletePeriod,
    };
  },
});
</script>

<style scoped>
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}
</style>
