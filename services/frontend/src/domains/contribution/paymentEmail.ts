import {ContributionEmailKind} from "@/services/api"

export const contributionEmailLabels: Record<ContributionEmailKind, string> = {
  [ContributionEmailKind.REMINDER]: "Contribution reminder",
  [ContributionEmailKind.INCASSO_NOTIFICATION]: "Incasso notification",
}
