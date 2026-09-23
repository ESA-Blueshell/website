<template>
  <v-main>
    <island
      class="home-island"
      testid="home-island"
    >
      <home-hero />
      <upcoming-band />
      <casual-band />
      <lineup-band />
      <perk-band
        accent="var(--color-acid)"
        :columns="3"
        eyebrow="You can be a member too"
        heading="What membership gets you"
        mark="tick"
        :perks="HOME_PERKS"
        testid="home-perks"
      >
        <cut-button
          href="/membership/signup"
          testid="home-perks-signup"
          tone="solid"
        >
          Become a member
        </cut-button>
      </perk-band>
      <discord-band />
      <partner-wall
        eyebrow="The organisations who make Blueshell possible"
        heading="Our partners"
        :partners="HOME_PARTNERS"
        testid="home-partners"
      >
        <cut-button
          href="/partners/become-a-partner"
          testid="home-partners-more"
        >
          Get to know more
        </cut-button>
      </partner-wall>
      <call-band v-bind="HOME_CALL" />
    </island>
  </v-main>
</template>

<script lang="ts" setup>
import CallBand from "@/components/island/CallBand.vue"
import CutButton from "@/components/island/CutButton.vue"
import Island from "@/components/island/Island.vue"
import CasualBand from "@/domains/association/island/CasualBand.vue"
import HomeHero from "@/domains/association/island/HomeHero.vue"
import PartnerWall from "@/domains/association/island/PartnerWall.vue"
import PerkBand from "@/domains/association/island/PerkBand.vue"
import UpcomingBand from "@/domains/association/island/UpcomingBand.vue"
import {HOME_CALL, HOME_PARTNERS, HOME_PERKS} from "@/domains/association"
import DiscordBand from "@/domains/discord/island/DiscordBand.vue"
import LineupBand from "@/domains/esports/island/LineupBand.vue"
</script>

<style lang="scss" scoped>
/* The island root fills a page; here it is only as tall as its bands. */
.home-island {
  min-height: 0;
}
</style>
