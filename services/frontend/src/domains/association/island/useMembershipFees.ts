import {computed, onMounted, ref, type ComputedRef} from "vue"
import {loadCurrentContributionPeriod, type ContributionPeriod} from "../adapters/association"
import {feeQuote, type FeeQuote} from "../fees"

/**
 * What membership costs this year, read once for the band that quotes it.
 *
 * Nothing where the read has not landed and nothing where no period is recorded: the band says
 * what each fee is for either way, and only the amounts wait. It cannot invent a price.
 */
export function useMembershipFees(): {quote: ComputedRef<FeeQuote | null>} {
  const period = ref<ContributionPeriod | null>(null)

  onMounted(async () => {
    period.value = await loadCurrentContributionPeriod()
  })

  return {quote: computed(() => feeQuote(period.value))}
}
