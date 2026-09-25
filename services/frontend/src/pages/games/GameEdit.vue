<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import GameEditor from "@/domains/games/components/GameEditor.vue"
import {type CasualGame, useCasualGames} from "@/domains/games"
import {useReturnTo} from "@/composables/useReturnTo"
import NotFound from "@/pages/NotFound.vue"

defineOptions({name: "GameEditPage"})

/**
 * One game added or corrected, from /casual or /competition. It goes back where it came from,
 * and to the game's own page in that area once one exists and the page it came from is gone.
 */
const route = useRoute()
const router = useRouter()
const area = computed(() => (route.meta.area === "competition" ? "competition" : "casual"))
const {games, ready} = useCasualGames()

const adding = computed(() => route.params.slug == null)
const answered = ref(false)
void ready.then(() => { answered.value = true })
const game = computed<CasualGame | null>(() => games.value.find(one => one.slug === route.params.slug) ?? null)
const enterIn = computed(() => (route.query.season == null ? null : Number(route.query.season)))

/** A new game added from a season's page goes back to that season, where it is now entered. */
const fallback = adding.value
  ? enterIn.value == null ? `/${area.value}` : `/competition/seasons/${enterIn.value}/edit`
  : `/${area.value}/${String(route.params.slug)}`
const back = useReturnTo(fallback)

/** The page it came from, unless that was the game's own page at an address it no longer has. */
const saved = (now: CasualGame) => {
  const old = `/${area.value}/${String(route.params.slug ?? "")}`
  const moved = !adding.value && router.resolve(back).path === old && now.slug !== route.params.slug
  if (adding.value && area.value === "casual") void router.replace(`/casual/${now.slug}`)
  else void router.replace(moved ? back.replace(old, `/${area.value}/${now.slug}`) : back)
}
</script>

<template>
  <game-editor
    v-if="adding || game"
    :area="area"
    :back="back"
    :enter-in="enterIn"
    :game="adding ? null : game"
    @cancel="router.replace(back)"
    @removed="router.replace(`/${area}`)"
    @saved="saved"
  />
  <not-found v-else-if="answered" />
</template>
