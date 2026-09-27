<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute} from "vue-router"
import CasualGamePage from "@/domains/games/components/CasualGamePage.vue"
import {useCasualGames} from "@/domains/games"
import NotFound from "@/pages/NotFound.vue"

defineOptions({name: "CasualGameBySlugPage"})

/**
 * One game's page, found by the address its record names. A removed game is not listed, so its
 * address reads as not found like any address no game answers to.
 */
const route = useRoute()
const {games, ready} = useCasualGames()

const slug = computed(() => String(route.params.slug ?? ""))
// Nothing is known until the games answer; until then this is neither a game nor a miss.
const answered = ref(false)
void ready.then(() => { answered.value = true })

const record = computed(() => games.value.find(game => game.slug === slug.value) ?? null)

watch(record, (found) => {
  if (found) document.title = `${found.name} — Blueshell`
}, {immediate: true})
</script>

<template>
  <casual-game-page
    v-if="record"
    :key="record.code"
    :game="record"
  />
  <not-found v-else-if="answered" />
</template>
