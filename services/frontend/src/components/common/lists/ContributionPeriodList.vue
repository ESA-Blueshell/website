<template>
  <v-card
    class="overflow-hidden"
    data-testid="contribution-period-list"
  >
    <div class="px-5 d-flex align-center justify-space-between">
      <div class="d-flex align-center">
        <h2 class="contrib-title">
          Contribution Periods
        </h2>
      </div>
    </div>

    <div class="px-5">
      <v-row
        class="d-flex align-center mb-2"
        no-gutters
      >
        <v-col class="flex-grow-1">
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
              v-slot="{ toggle, selectedClass, isSelected }"
              :value="period.id"
            >
              <div
                @mouseleave="hoveredPeriodId = null"
                @mouseover="hoveredPeriodId = period.id ?? null"
              >
                <v-btn
                  :class="[
                    'text-body-1',
                    'text-none',
                    isSelected && selectedClass,
                    'ma-2'
                  ]"
                  :elevation="isSelected ? 0 : 4"
                  :data-testid="`contribution-period-select-btn-${period.id}`"
                  :variant="isSelected ? 'flat' : 'elevated'"
                  @click="toggle"
                >
                  {{ formatPeriod(period) }}
                  <v-icon
                    v-if="hoveredPeriodId === period.id"
                    class="edit-icon"
                    :data-testid="`contribution-period-edit-btn-${period.id}`"
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

        <v-col
          class="pl-2"
          cols="auto"
        >
          <v-btn
            data-testid="contribution-period-add-btn"
            icon
            @click="openAddPeriodDialog"
          >
            <v-icon>mdi-plus</v-icon>
          </v-btn>
        </v-col>
      </v-row>

      <contribution-period-dialog
        v-model:show-dialog="showAddPeriodDialog"
        :contribution-period="selectedPeriod"
        @changed="onPeriodChanged"
        @delete="deleteContributionPeriod"
      />

      <delete-confirmation-dialog
        v-model="deleteDialog"
        :message="`Are you sure you want to delete the contribution period from ${formatPeriod(selectedPeriod)}?`"
        title="Confirm Period Deletion"
        @confirm="confirmDeleteContributionPeriod"
      />
    </div>
  </v-card>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {DateTime} from "luxon"
import ContributionPeriodDialog from "@/components/common/modals/ContributionPeriodDialog.vue"
import DeleteConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import {type ContributionPeriodResponse, deleteContributionPeriodById, findContributionPeriods} from "@/services/api"

defineOptions({name: "ContributionPeriodList"})

const emit = defineEmits<{
  (e: "update:contribution-period", value: ContributionPeriodResponse | undefined): void;
}>()

const contributionPeriods = ref<ContributionPeriodResponse[]>([])
const selectedPeriodId = ref<number | undefined>()
const hoveredPeriodId = ref<number | null>(null)
const deleteDialog = ref(false)
const selectedPeriod = ref<ContributionPeriodResponse | null>(null)
const isEditing = ref(false)
const showAddPeriodDialog = ref(false)

const formatPeriod = (period?: ContributionPeriodResponse | null) => {
  if (!period) return ""
  const start = DateTime.fromISO(period.startDate).toFormat("dd/MM/yyyy")
  const end = DateTime.fromISO(period.endDate).toFormat("dd/MM/yyyy")
  return `${start} - ${end}`
}

const getContributionPeriods = async () => {
  const response = await findContributionPeriods()
  contributionPeriods.value = (response.data ?? [])
    .sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime())
  if (contributionPeriods.value.length > 0) {
    selectedPeriodId.value = contributionPeriods.value.at(-1)!.id
    selectedPeriodIdChanged(selectedPeriodId.value)
  } else {
    selectedPeriodId.value = undefined
    selectedPeriodIdChanged(undefined)
  }
}

const openAddPeriodDialog = () => {
  isEditing.value = false
  selectedPeriod.value = null
  showAddPeriodDialog.value = true
}

const openEditPeriodDialog = (period: ContributionPeriodResponse) => {
  isEditing.value = true
  selectedPeriod.value = period
  showAddPeriodDialog.value = true
}

const deleteContributionPeriod = () => {
  deleteDialog.value = true
}

const confirmDeleteContributionPeriod = async () => {
  isEditing.value = false
  deleteDialog.value = false
  if (selectedPeriodId.value != null) {
    await deleteContributionPeriodById({path: {id: selectedPeriodId.value}})
  }
  selectedPeriod.value = null
  selectedPeriodId.value = undefined
  await getContributionPeriods()
}

const selectedPeriodIdChanged = (id: number | undefined) => {
  const period = contributionPeriods.value.find((cp) => cp.id === id)
  emit("update:contribution-period", period)
}

/** Refresh periods and keep/restore selection after dialog save */
const onPeriodChanged = async (p: ContributionPeriodResponse) => {
  await getContributionPeriods()
  selectedPeriodId.value = p?.id
  selectedPeriodIdChanged(selectedPeriodId.value)
}

onMounted(() => {
  getContributionPeriods()
})
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

.text-none {
  text-transform: none;
}
</style>
