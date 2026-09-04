<script lang="ts" setup>
import Island from "@/components/island/Island.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import BandRule from "@/components/island/BandRule.vue"
import CallBand from "@/components/island/CallBand.vue"
import JoinHero from "@/domains/association/island/JoinHero.vue"
import NumberBand from "@/domains/association/island/NumberBand.vue"
import PerkBand, {type Perk} from "@/domains/association/island/PerkBand.vue"
import FeeBand from "@/domains/association/island/FeeBand.vue"
import {MEMBERSHIP_CALL} from "@/domains/association/island/membershipCall"
import {useAssociationNumbers} from "@/domains/association/island/useAssociationNumbers"
import {useMembershipFees} from "@/domains/association/island/useMembershipFees"

/**
 * The page that sells membership: the pitch, the numbers behind it, what a member gets, what it
 * costs and one way in.
 *
 * The signup form is somewhere else and is not being redesigned, so this page quotes the fees
 * rather than reusing the form's fee component: both read the contribution period the
 * association is charging for.
 */
defineOptions({name: "MembershipPage"})

const HEAD = {
  eyebrow: "Join Blueshell",
  heading: "The largest student gaming",
  headingTail: "association in the country",
  body: "Blueshell is the gaming association of the University of Twente: a few hundred people "
    + "from every study, playing together online and in person since 2017. Membership is what "
    + "gets you in the door.",
}

const PITCH = "One membership: every event, a committee to run something in, a shot at a roster, "
  + "and a Discord full of people already playing."

const PERKS: Perk[] = [
  {
    id: "events",
    title: "Everything on the calendar",
    body: "Members-only game nights, LAN parties, tournaments and pub quizzes, many of them in "
      + "the Predator Esports Lounge in the Bastille, and a discount on the ones that cost "
      + "anything.",
  },
  {
    id: "discord",
    title: "The whole Discord",
    body: "The member channels on a server of over a thousand gamers. It is where teams get put "
      + "together, where the events are planned and where most of the association actually "
      + "spends its evenings.",
  },
  {
    id: "committees",
    title: "A committee to run something in",
    body: "Committees run the events, the website, the Nintendo nights and the escape rooms. "
      + "Join one and organise something you have never organised before.",
  },
  {
    id: "esports",
    title: "A shot at a roster",
    body: "Tryouts every season for the teams Blueshell fields in the Dutch College Esports "
      + "Series and the Dutch Student League, which the association has won three times in a "
      + "row. Nobody is turned away for being new.",
  },
]

const TERMS = [
  "Regular membership runs from 1 September to 31 August.",
  "You do not need a Union Card to become a member of Blueshell.",
]

const {figures} = useAssociationNumbers()
const {quote} = useMembershipFees()
</script>

<template>
  <v-main>
    <island testid="membership-island">
      <header-band v-bind="HEAD" />

      <join-hero
        action="Become a member"
        href="/membership/signup"
        :pitch="PITCH"
      />

      <number-band :figures="figures" />

      <perk-band
        heading="What membership gets you"
        :perks="PERKS"
      />

      <fee-band
        heading="What it costs"
        :quote="quote"
        :terms="TERMS"
      />

      <band-rule testid="membership-rule" />

      <call-band v-bind="MEMBERSHIP_CALL" />
    </island>
  </v-main>
</template>
