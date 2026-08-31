<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute} from "vue-router"
import EsportsGamePage from "@/domains/esports/components/EsportsGamePage.vue"
import NotFound from "@/pages/NotFound.vue"
import {useGames} from "@/domains/esports/island/useGames"

defineOptions({name: "GameBySlugPage"})

/**
 * Every game's page, found by the address its record names.
 *
 * A game used to gain a page by somebody writing a component and a route for it, which is why
 * Trackmania had copy written for it and no way to reach it. A row is enough now.
 */
const route = useRoute()
const {ready, bySlug} = useGames()

const slug = computed(() => String(route.params.slug ?? ""))
// Nothing is known until the records answer; until then this is neither a game nor a miss.
const answered = ref(false)
void ready.then(() => { answered.value = true })

const record = computed(() => bySlug(slug.value))

watch(record, (found) => {
  if (found) document.title = `${found.name} — Blueshell Esports`
}, {immediate: true})
</script>

<template>
  <esports-game-page
    v-if="record"
    :key="record.code"
    :game="record.code"
  />
  <not-found v-else-if="answered" />
</template>
