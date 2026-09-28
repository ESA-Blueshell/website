<script setup lang="ts">
import {PARTNER_PAGES, type PartnerSlug} from "@/domains/association"
import PartnerPage from "@/domains/association/island/PartnerPage.vue"

const {slug} = defineProps<{slug: PartnerSlug}>()
</script>

<template>
  <v-main>
    <partner-page :content="PARTNER_PAGES[slug]" />
  </v-main>
</template>
