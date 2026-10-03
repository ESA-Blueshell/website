<script lang="ts" setup>
/* A pending membership and the first contribution that makes it active (api ADR-036). Shows
   nothing for anyone without a pending membership. */
import {computed, onMounted, ref} from "vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {type FirstContribution, readFirstContribution} from "@/domains/contribution"
import {feeTypeLabels} from "@/utils/feePreview"

defineOptions({name: "PendingMembership"})

const owed = ref<FirstContribution | null>(null)

const what = computed(() => {
  const first = owed.value
  if (!first?.feeType || first.amount == null || !first.periodStartDate || !first.periodEndDate) return null
  const years = `${first.periodStartDate.slice(0, 4)}-${first.periodEndDate.slice(0, 4)}`
  return `€ ${first.amount.toFixed(2)}, the ${feeTypeLabels[first.feeType].toLowerCase()} for ${years}`
})

onMounted(async () => {
  owed.value = await readFirstContribution()
})
</script>

<template>
  <notice-box
    v-if="owed"
    testid="pending-membership"
    title="Your membership is pending"
    tone="warning"
  >
    <p>
      You become a member once your first contribution is paid<template v-if="what">
        : {{ what }}
      </template>.
      The treasurer records it when it arrives. Until then, members-only events and channels stay closed.
    </p>
  </notice-box>
</template>
