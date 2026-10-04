<script lang="ts" setup>
/* One contribution period: its dates, fees and half-year cutoff, or an empty form for a new one.
   Deleting sits in a danger zone and asks for the period's name first. */
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import TextInput from "@/components/island/TextInput.vue"
import ContributionPeriodForm from "@/components/management/ContributionPeriodForm.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import {type ContributionPeriodResponse, deletePeriod, listPeriods} from "@/domains/contribution"
import {$handleNetworkError} from "@/plugins/handleNetworkError"

defineOptions({name: "ContributionPeriodPage"})

const route = useRoute()
const router = useRouter()

const id = computed(() => (route.params.id ? Number(route.params.id) : null))
const period = ref<ContributionPeriodResponse | null>(null)
const loaded = ref(false)
const typed = ref("")

/** What the period is called on the strip and in the danger zone, such as 2025–26. */
const name = computed(() => (period.value ? `${period.value.startDate.slice(0, 4)}–${period.value.endDate.slice(2, 4)}` : ""))
const mayDelete = computed(() => typed.value.trim() === name.value)

const onSaved = (saved: ContributionPeriodResponse) => router.push(`/management/contributions/${saved.id}`)
const back = () => router.push(id.value ? `/management/contributions/${id.value}` : "/management/contributions")

const remove = async () => {
  if (!period.value || !mayDelete.value) return
  try {
    await deletePeriod(period.value.id)
    await router.push("/management/contributions")
  } catch (error) {
    $handleNetworkError(error)
  }
}

onMounted(async () => {
  if (id.value != null) {
    try {
      period.value = (await listPeriods()).find((one) => one.id === id.value) ?? null
    } catch (error) {
      $handleNetworkError(error)
    }
  }
  loaded.value = true
})
</script>

<template>
  <management-page
    :back="{to: id ? `/management/contributions/${id}` : '/management/contributions', label: 'Contributions'}"
    eyebrow="Members"
    testid="contribution-period-page"
    :title="id ? `Contribution period ${name}` : 'New contribution period'"
  >
    <template #lede>
      The dates of the period, the three fees and the date after which a new member pays the half-year fee.
    </template>

    <p
      v-if="loaded && id && !period"
      class="period__note"
      data-testid="contribution-period-missing"
    >
      There is no contribution period {{ id }}.
    </p>

    <div
      v-else-if="loaded"
      class="period"
    >
      <contribution-period-form
        :contribution-period="period ?? undefined"
        @cancelled="back"
        @changed="onSaved"
      />

      <notice-box
        v-if="period"
        testid="contribution-period-danger"
        title="Delete this period"
        tone="danger"
      >
        <div class="period__danger">
          <p>
            Deleting the period also deletes its contributions. Type <strong>{{ name }}</strong> to delete it.
          </p>
          <form-field
            v-slot="field"
            label="The period's name"
          >
            <text-input
              v-model="typed"
              :control-id="field.controlId"
              :placeholder="name"
              testid="contribution-period-delete-name"
            />
          </form-field>
          <cut-button
            :disabled="!mayDelete"
            testid="contribution-period-delete-btn"
            tone="danger"
            @click="remove"
          >
            Delete this period
          </cut-button>
        </div>
      </notice-box>
    </div>
  </management-page>
</template>

<style scoped>
.period {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  padding-top: 1.2rem;
}

.period__note {
  padding-top: 1.2rem;
  color: var(--color-ash);
}

.period__danger {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.6rem;
}

.period__danger :deep(.island-field) {
  width: 16rem;
  max-width: 100%;
}
</style>
