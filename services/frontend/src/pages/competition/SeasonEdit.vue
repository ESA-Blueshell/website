<script lang="ts" setup>
import {computed, ref, shallowRef, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import SeasonEditor from "@/domains/esports/components/SeasonEditor.vue"
import {type Season, useSeasons} from "@/domains/esports"
import {useReturnTo} from "@/composables/useReturnTo"
import NotFound from "@/pages/NotFound.vue"

defineOptions({name: "SeasonEditPage"})

/**
 * A season added or corrected on its own page, and back to the competition page it was opened
 * from, on the season that was saved.
 */
const route = useRoute()
const router = useRouter()
const back = useReturnTo("/competition")
const {seasons, ready} = useSeasons()

const adding = computed(() => route.params.id == null)
const answered = ref(false)
void ready.then(() => { answered.value = true })
/*
 * Held once found: removing the season reads the strip again before the editor says so, and an
 * editor dropped the moment its season left the list would never say it.
 */
const listed = computed(() => seasons.value.find(one => one.id === Number(route.params.id)) ?? null)
const found = shallowRef<Season | null>(null)
watch(listed, now => { if (now) found.value = now }, {immediate: true})
const season = computed<Season | null>(() => listed.value ?? found.value)

/** The page it came from, on [id]'s season, or on none where the season is gone. */
const backOn = (id: number | null) => {
  const target = router.resolve(back)
  const {season: _shown, ...query} = target.query
  return {path: target.path, query: id == null ? query : {...query, season: String(id)}}
}
</script>

<template>
  <season-editor
    v-if="adding || season"
    :back="back"
    :season="adding ? null : season"
    @cancel="router.replace(back)"
    @removed="router.replace(backOn(null))"
    @saved="saved => router.replace(backOn(saved.id))"
  />
  <not-found v-else-if="answered" />
</template>
