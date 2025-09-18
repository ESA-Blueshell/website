<template>
  <div>
    <p class="text-h3">
      Contribution Periods
    </p>

    <v-row class="d-flex align-center mb-4">
      <v-col cols="11">
        <v-slide-group
          v-model="selectedPeriodId"
          :show-arrows="true"
          center-active
          mandatory
          selected-class="bg-primary"
          @update:model-value="selectedPeriodIdChanged"
        >
          <v-slide-group-item
            v-for="period in contributionPeriods"
            :key="period.id"
            v-slot="{ toggle, selectedClass }"
            :value="period.id"
          >
            <div
              @mouseover="hoveredPeriodId = period.id ?? null"
              @mouseleave="hoveredPeriodId = null"
            >
              <v-btn
                :class="['mr-2', selectedClass]"
                class="mr-2"
                @click="toggle"
              >
                {{ formatPeriod(period) }}
                <v-icon
                  v-if="hoveredPeriodId === period.id"
                  class="edit-icon"
                  style="padding-left: 10px"
                  @click.stop="openEditPeriodDialog(period)"
                >
                  mdi-pencil
                </v-icon>
              </v-btn>
            </div>
          </v-slide-group-item>
        </v-slide-group>
      </v-col>
      <v-col cols="1">
        <v-btn
          icon
          class="ml-auto"
          @click="openAddPeriodDialog"
        >
          <v-icon>mdi-plus</v-icon>
        </v-btn>
      </v-col>
    </v-row>

    <!-- Contribution Period Dialog -->
    <contribution-period-dialog
      v-model="showAddPeriodDialog"
      :is-editing="isEditing"
      :selected-period="selectedPeriod"
      :contribution-periods="contributionPeriods"
      @refresh-periods="getContributionPeriods"
      @delete="deleteContributionPeriod"
    />

    <!-- Delete Confirmation Dialog for Periods -->
    <delete-confirmation-dialog
      v-model="deleteDialog"
      title="Confirm Period Deletion"
      :message="`Are you sure you want to delete the contribution period from ${formatPeriod(selectedPeriod)}?`"
      @confirm="confirmDeleteContributionPeriod"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { DateTime } from 'luxon';
import ContributionPeriodDialog from '@/views/member/ContributionPeriodDialog.vue';
import DeleteConfirmationDialog from '@/components/DeletionConfirmationDialog.vue';
import { type ContributionPeriodDto, findContributionPeriods } from '@/lib';

defineOptions({ name: 'ContributionPeriodList' });

const emit = defineEmits<{
  (e: 'selected-period-id-changed', value: number | null): void;
}>();

const contributionPeriods = ref<ContributionPeriodDto[]>([]);
const selectedPeriodId = ref<number | null>(null); // ← no default ID
const hoveredPeriodId = ref<number | null>(null);
const deleteDialog = ref(false);
const selectedPeriod = ref<ContributionPeriodDto | null>(null);
const isEditing = ref(false);
const showAddPeriodDialog = ref(false);

const formatPeriod = (period?: ContributionPeriodDto | null) => {
  if (!period) return '';
  const start = DateTime.fromISO(period.startDate).toFormat('dd/MM/yyyy');
  const end = DateTime.fromISO(period.endDate).toFormat('dd/MM/yyyy');
  return `${start} - ${end}`;
};

const getContributionPeriods = async () => {
  const response = await findContributionPeriods();
  contributionPeriods.value = response.data ?? [];
  if (contributionPeriods.value.length > 0) {
    selectedPeriodId.value =
      contributionPeriods.value[contributionPeriods.value.length - 1].id ?? null;
    selectedPeriodIdChanged(selectedPeriodId.value);
  } else {
    selectedPeriodId.value = null;
    selectedPeriodIdChanged(null);
  }
};

const openAddPeriodDialog = () => {
  isEditing.value = false;
  selectedPeriod.value = null; // ← blank object; dialog will initialize its own form
  showAddPeriodDialog.value = true;
};

const openEditPeriodDialog = (period: ContributionPeriodDto) => {
  isEditing.value = true;
  selectedPeriod.value = period;
  showAddPeriodDialog.value = true;
};

const deleteContributionPeriod = () => {
  deleteDialog.value = true;
};

const confirmDeleteContributionPeriod = async () => {
  isEditing.value = false;
  deleteDialog.value = false;
  selectedPeriod.value = null;
  selectedPeriodId.value = null; // ← clear selection
  await getContributionPeriods();
};

const selectedPeriodIdChanged = (value: number | null) => {
  emit('selected-period-id-changed', value);
};

onMounted(() => {
  getContributionPeriods();
});
</script>

<style lang="scss">

span {
  font-weight: bold;
}

.hover-shadow {
  transition: 0.3s ease-in-out;
}

.hover-shadow:hover {
  box-shadow: 0 4px 8px rgba(186, 181, 181, 0.2);
  border-radius: 50% !important;
}
</style>
