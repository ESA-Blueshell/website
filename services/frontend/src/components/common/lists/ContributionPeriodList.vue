<template>
  <v-card
    class="overflow-hidden"
    data-testid="contribution-period-list"
  >
    <div class="px-5 pt-3 pb-2 d-flex align-center justify-space-between">
      <div class="d-flex align-center">
        <h2 class="contrib-title ma-0">
          Contribution Periods
        </h2>
      </div>
    </div>

    <div class="px-5 pb-4">
      <v-alert
        v-if="periodsUnread"
        class="mb-3"
        data-testid="contribution-period-list-unread"
        type="warning"
        variant="tonal"
      >
        The contribution periods could not be read, so none are offered. Reload the page to
        try again.
      </v-alert>

      <div class="d-flex align-center flex-nowrap">
        <div class="contrib-periods-scroller">
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
                    'ma-1'
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
        </div>

        <v-btn
          class="ml-2 flex-shrink-0"
          data-testid="contribution-period-add-btn"
          icon
          @click="openAddPeriodDialog"
        >
          <v-icon>mdi-plus</v-icon>
        </v-btn>
      </div>

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
import {$handleNetworkError} from "@/plugins/handleNetworkError"
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
/** Set where the list could not be read, which is not an association with no periods. */
const periodsUnread = ref(false)
const selectedPeriodId = ref<number | undefined>()
const hoveredPeriodId = ref<number | null>(null)
const deleteDialog = ref(false)
const selectedPeriod = ref<ContributionPeriodResponse | null>(null)
const showAddPeriodDialog = ref(false)

const formatPeriod = (period?: ContributionPeriodResponse | null) => {
  if (!period) return ""
  const start = DateTime.fromISO(period.startDate).toFormat("dd/MM/yyyy")
  const end = DateTime.fromISO(period.endDate).toFormat("dd/MM/yyyy")
  return `${start} - ${end}`
}

const getContributionPeriods = async () => {
  const response = await findContributionPeriods()
  // A list that could not be read is not an association with no periods, and everything the
  // page says about who paid hangs off which period is picked.
  if (response.error || !response.data) {
    periodsUnread.value = true
    contributionPeriods.value = []
    selectedPeriodId.value = undefined
    selectedPeriodIdChanged(undefined)
    return
  }
  periodsUnread.value = false
  contributionPeriods.value = response.data
    .slice()
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
  selectedPeriod.value = null
  showAddPeriodDialog.value = true
}

const openEditPeriodDialog = (period: ContributionPeriodResponse) => {
  selectedPeriod.value = period
  showAddPeriodDialog.value = true
}

const deleteContributionPeriod = () => {
  deleteDialog.value = true
}

const confirmDeleteContributionPeriod = async () => {
  deleteDialog.value = false
  if (selectedPeriodId.value != null) {
    try {
      await deleteContributionPeriodById({path: {id: selectedPeriodId.value}, throwOnError: true})
    } catch (error) {
      // The period is still there, so the selection stays on it rather than resetting
      // to a list that would show it again anyway.
      $handleNetworkError(error)
      return
    }
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

// Let the slide-group shrink below its content width so it scrolls
// internally instead of pushing the add (+) button onto a new row.
.contrib-periods-scroller {
  flex: 1 1 0;
  min-width: 0;
}
</style>
