/**
 * The association domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001). Re-exported by name rather than with
 * `export *`, because the list of names is the promise being made.
 *
 * The bands are exported as components because a page is what draws them, and a page may not
 * reach into a domain to find one.
 */
export {MEMBERSHIP_CALL} from "./island/membershipCall"
export {useAssociationNumbers} from "./island/useAssociationNumbers"
export {useMembershipFees} from "./island/useMembershipFees"
export {MILESTONES, type Milestone} from "./historyAxis"
export type {Perk} from "./island/PerkBand.vue"
export type {Field} from "./island/ReachChart.vue"
