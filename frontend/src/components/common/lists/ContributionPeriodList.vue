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
              @mouseleave="hoveredPeriodId = null"
              @mouseover="hoveredPeriodId = period.id ?? null"
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
          class="ml-auto"
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
      @delete="deleteContributionPeriod"
      @refresh-periods="getContributionPeriods"
    />

    <delete-confirmation-dialog
      v-model="deleteDialog"
      :message="`Are you sure you want to delete the contribution period from ${formatPeriod(selectedPeriod)}?`"
      title="Confirm Period Deletion"
      @confirm="confirmDeleteContributionPeriod"
    />
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {DateTime} from "luxon"
import ContributionPeriodDialog from "@/components/common/modals/ContributionPeriodDialog.vue"
import DeleteConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import {type ContributionPeriod, deleteContributionPeriodById, findContributionPeriods} from "@/services/api"

defineOptions({name: "ContributionPeriodList"})

const emit = defineEmits<{
  (e: "update:contribution-period", value: ContributionPeriod | undefined): void;
}>()

const contributionPeriods = ref<ContributionPeriod[]>([])
const selectedPeriodId = ref<number | undefined>()
const hoveredPeriodId = ref<number | null>(null)
const deleteDialog = ref(false)
const selectedPeriod = ref<ContributionPeriod | null>(null)
const isEditing = ref(false)
const showAddPeriodDialog = ref(false)

const formatPeriod = (period?: ContributionPeriod | null) => {
  if (!period) return ""
  const start = DateTime.fromISO(period.startDate).toFormat("dd/MM/yyyy")
  const end = DateTime.fromISO(period.endDate).toFormat("dd/MM/yyyy")
  return `${start} - ${end}`
}

const getContributionPeriods = async () => {
  const response = await findContributionPeriods()
  contributionPeriods.value = response.data ?? []
  if (contributionPeriods.value.length > 0) {
    selectedPeriodId.value = contributionPeriods.value[contributionPeriods.value.length - 1]!.id
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

const openEditPeriodDialog = (period: ContributionPeriod) => {
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
  await deleteContributionPeriodById({
    path: {
      id: selectedPeriodId.value!,
    },
  })
  selectedPeriod.value = null
  selectedPeriodId.value = undefined
  await getContributionPeriods()
}

const selectedPeriodIdChanged = (id: number | undefined) => {
  emit("update:contribution-period", contributionPeriods.value.find((cp) => cp.id === id)!)
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
</style>
