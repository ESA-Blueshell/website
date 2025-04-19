<template>
  <div>
    <p class="text-h3">
      ContributionModel Periods
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
              @mouseover="hoveredPeriodId = period.id || 0"
              @mouseleave="hoveredPeriodId = 0"
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

    <!-- ContributionModel Period Dialog -->
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

<script lang="ts">
import {defineComponent, onMounted, ref} from 'vue';
import ContributionPeriodDialog from '@/views/login/member/ContributionPeriodDialog.vue';
import DeleteConfirmationDialog from '@/components/DeletionConfirmationDialog.vue';
import {ContributionPeriodService} from '@/services';
import type {ContributionPeriodModel} from '@/models';
import {DateTime} from 'luxon';

export default defineComponent({
  name: 'ContributionPeriodList',
  components: {
    ContributionPeriodDialog,
    DeleteConfirmationDialog,
  },
  emits: ['selected-period-id-changed'],
  setup(props, {emit}) {
    const contributionPeriods = ref([] as ContributionPeriodModel[]);
    const selectedPeriodId = ref(0);
    const hoveredPeriodId = ref(0);
    const deleteDialog = ref(false);
    const selectedPeriod = ref({} as ContributionPeriodModel);
    const isEditing = ref(false);
    const showAddPeriodDialog = ref(false);
    const contributionPeriodService = new ContributionPeriodService();

    const formatPeriod = (period: ContributionPeriodModel) => {
      if (!period) return '';
      const start = DateTime.fromISO(period.startDate).toFormat('dd/MM/yyyy');
      const end = DateTime.fromISO(period.endDate).toFormat('dd/MM/yyyy');
      return `${start} - ${end}`;
    };

    const getContributionPeriods = async () => {
      contributionPeriods.value = await contributionPeriodService.getContributionPeriods();
      if (contributionPeriods.value.length > 0) {
        selectedPeriodId.value = contributionPeriods.value[contributionPeriods.value.length - 1].id || 0;
        selectedPeriodIdChanged(selectedPeriodId.value)
      }
    };

    const openAddPeriodDialog = () => {
      isEditing.value = false;
      selectedPeriod.value = {} as ContributionPeriodModel;
      showAddPeriodDialog.value = true;
    };

    const openEditPeriodDialog = (period: ContributionPeriodModel) => {
      isEditing.value = true;
      selectedPeriod.value = period;
      showAddPeriodDialog.value = true;
    };

    const deleteContributionPeriod = () => {
      deleteDialog.value = true;
    }

    const confirmDeleteContributionPeriod = async () => {
      await contributionPeriodService.delete(selectedPeriod.value);
      isEditing.value = false;
      deleteDialog.value = false;
      selectedPeriod.value = {} as ContributionPeriodModel;
      selectedPeriodId.value = 0;
      await getContributionPeriods();
    };

    const selectedPeriodIdChanged = (selectedPeriodId: number) => {
      emit('selected-period-id-changed', selectedPeriodId);
    };

    onMounted(() => {
      getContributionPeriods();
    });

    return {
      contributionPeriods,
      selectedPeriodId,
      hoveredPeriodId,
      deleteDialog,
      selectedPeriod,
      isEditing,
      showAddPeriodDialog,
      deleteContributionPeriod,
      formatPeriod,
      getContributionPeriods,
      openAddPeriodDialog,
      openEditPeriodDialog,
      confirmDeleteContributionPeriod,
      selectedPeriodIdChanged,
    };
  },
});
</script>

<style scoped>
span {
  font-weight: bold;
}

.hover-shadow {
  transition: 0.3s ease-in-out;
}

.hover-shadow:hover {
  box-shadow: 0 4px 8px rgba(186, 181, 181, 0.2);
  border-radius: 50%;
}
</style>
