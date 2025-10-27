<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {DateTime} from "luxon"
import {type ContributionPeriod, findCurrentContributionPeriod} from "@/services/api"

const props = withDefaults(defineProps<{
  isForm?: boolean
}>(), {
  isForm: false,
})

const contributionPeriod = ref<ContributionPeriod>()
const currentPeriod = ref(false)
const loading = ref(true)
const error = ref<string | null>(null)

const euros = new Intl.NumberFormat("nl-NL", {style: "currency", currency: "EUR"})

const formatCurrency = (amount?: number) => euros.format(amount ?? 0)

const formatPeriod = (period?: ContributionPeriod) => {
  if (!period?.startDate || !period?.endDate) return "N/A"
  const start = DateTime.fromISO(period.startDate).toFormat("yyyy")
  const end = DateTime.fromISO(period.endDate).toFormat("yyyy")
  return currentPeriod.value ? `${start}/${end}` : `${start}/${end}*`
}

async function getContributionPeriod() {
  try {
    const response = await findCurrentContributionPeriod()
    contributionPeriod.value = response.data

    const now = DateTime.now()
    const startDate = DateTime.fromISO(contributionPeriod.value?.startDate as string)
    const endDate = DateTime.fromISO(contributionPeriod.value?.endDate as string)
    currentPeriod.value = now >= startDate && now <= endDate
  } catch (err: unknown) {
    $handleNetworkError(err)
    error.value = "Failed to fetch current contribution period"
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void getContributionPeriod()
})
</script>

<template>
  <div>
    <strong>Contribution</strong><br>

    <div v-if="loading">
      Loading contribution information...
    </div>

    <div
      v-else-if="error"
      class="bg-error"
    >
      Error fetching contribution information: {{ error }}
    </div>

    <div v-else>
      <p v-if="props.isForm">
        The undersigned understands that they will need to pay the {{ formatPeriod(contributionPeriod) }} contribution
        fee, for which they will receive payment information by email.
      </p>
      <p>
        The membership fees for the academic year {{ formatPeriod(contributionPeriod) }} are:
      </p>
      <ul>
        <li><b>{{ formatCurrency(contributionPeriod?.fullYearFee) }}</b> for a full year membership</li>
        <li><b>{{ formatCurrency(contributionPeriod?.halfYearFee) }}</b> for a half-year membership*</li>
        <li><b>{{ formatCurrency(contributionPeriod?.alumniFee) }}</b> for an Alumni membership</li>
      </ul>
      <p class="text-body-1">
        <br>
        <span v-if="!currentPeriod">
          <strong>
            *The prices shown are for the previous year and are subject to change for the coming year at the
            General Members Meeting in September
          </strong>
          <br><br>
        </span>
        <span><strong>*A half-year membership can only be obtained between the months of February and July.</strong></span>
      </p>
    </div>
  </div>
</template>
