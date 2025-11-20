<template>
  <div style="position:relative;overflow: hidden;">
    <p
      class="mx-auto text-center text-h2 mt-16 text-white"
      style="z-index: 3;position: relative;"
    >
      Games we play
    </p>

    <div
      v-for="(category,i) in games"
      :key="`cat-${category.categoryName}-${i}`"
      class="mb-16"
    >
      <p
        class="mx-auto text-center text-h4 font-weight-light mb-3 text-white"
        style="z-index: 3;position: relative;"
      >
        {{ category.categoryName }}
      </p>

      <div
        class="mx-auto mb-6"
        style="max-width: 1400px; display: flex; flex-wrap: wrap; align-content: center; justify-content: center"
      >
        <div
          v-for="(game,j) in category.titles"
          :key="`grid-${category.categoryName}-${game.title}-${j}`"
          :class="{ 'elevation-8': (showPopup || hoverCarousel) && currentGame!==null && i===currentGame.y && j===currentGame.x }"
          :style="{
            cursor: game.esportsLink ? 'pointer' : 'auto',
            'background-color': !(theme.global.current.value.dark && (!showPopup && !hoverCarousel)) && ((!showPopup && !hoverCarousel) || (currentGame!==null && i===currentGame.y && j===currentGame.x )) ? '#F5F5F5aa' : '#F5F5F544',
            width: $vuetify.display.smAndUp ? '85px' : '70px',
            height: $vuetify.display.smAndUp ? '85px' : '70px',
          }"
          class="mx-3 my-2 pa-2"
          style="border-radius: 10px;z-index: 3;transition: .3s cubic-bezier(.25,.8,.5,1) !important;"

          @click="handleGameClick(game.esportsLink)"
          @mouseenter="hover(i,j)"
          @mouseleave="unhover"
        >
          <v-lazy
            :height="$vuetify.display.smAndUp ? '69px' : '54px'"
            :options="{'threshold':0.1}"
            :width="$vuetify.display.smAndUp ? '69px' : '54px'"
          >
            <v-img :src="game.icon" />
          </v-lazy>
        </div>
      </div>
    </div>

    <div style="top:0;left:0;width:100%;height:100%;position: absolute;z-index: 0;background: rgba(0, 0, 0, 1)" />

    <div style="top:0;left:0;width:100%;height:100%;position: absolute;z-index: 2;background: rgba(0, 0, 0, 0.6)" />

    <div style="top:0;left:0;width:100%;height:100%;position: absolute;z-index: 1;">
      <v-carousel
        id="carousel"
        v-model="currentGameIndex"
        :show-arrows="false"
        hide-delimiters
        style="height: 100%;z-index: 1;"
        theme="light"
      >
        <v-carousel-item
          v-for="(game) in flatGames"
          :key="`slide-${game.__uid}`"
          style="z-index: 1;"
          @mouseenter="hover(null,null)"
          @mouseleave="unhover"
        >
          <v-sheet
            height="100%"
            style="z-index: 1;background: rgba(0, 0, 0, 1)"
          >
            <!-- Do NOT wrap in v-lazy here -->
            <v-img
              :src="game.bg"
              cover
              style="position: absolute;top: 0;height: 100%;width: 100%;z-index: 1;filter: blur(3px);-webkit-filter: blur(3px);"
              transition="fade-transition"
            >
              <!-- lightweight placeholder while loading -->
              <template #placeholder>
                <div style="width:100%;height:100%;background:linear-gradient(180deg,#1f1f1f,#0a0a0a);" />
              </template>
              <!-- graceful fallback on error -->
              <template #error>
                <div style="width:100%;height:100%;background:linear-gradient(180deg,#1f1f1f,#0a0a0a);" />
              </template>
            </v-img>
          </v-sheet>
        </v-carousel-item>
      </v-carousel>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {$goto} from "@/plugins/goto.ts"
import {useTheme} from "vuetify"

interface Game {
  title: string;
  icon: string;
  bg: string;
  esportsLink?: string;
}

interface GameCategory {
  categoryName: string;
  titles: Game[];
}

interface CurrentGame {
  x: number;
  y: number;
}

interface Props {
  games: GameCategory[];
}

const theme = useTheme()
const props = defineProps<Props>()

const showPopup = ref<number>(0)
const currentGame = ref<CurrentGame | null>(null)
const currentGameIndex = ref<number>(0)
const hoverCarousel = ref<boolean>(false)

const flatGames = computed(() =>
  props.games.flatMap((gc, gy) =>
    gc.titles.map((t, gx) => ({
      ...t,
      __uid: `${gc.categoryName}::${t.title}::${gy}-${gx}`, // unique per slide
    })),
  ),
)

const handleGameClick = (esportsLink?: string): void => {
  if (esportsLink) $goto(esportsLink)
}

const hover = (i: number | null, j: number | null): void => {
  showPopup.value++
  if (i !== null && j !== null) {
    currentGame.value = {y: i, x: j}

    let newIndex = 0
    for (let k = 0; k < i; k++) {
      newIndex += props.games[k]!.titles.length
    }
    newIndex += j
    currentGameIndex.value = newIndex
  }
}

const unhover = (): void => {
  setTimeout(() => {
    showPopup.value = Math.max(0, showPopup.value - 1)
  }, 150)
}

const preloaded = new Set<string>()
const preload = (src?: string) => {
  if (!src || preloaded.has(src)) return
  const img = new Image()
  img.src = src
  preloaded.add(src)
}

const preloadAround = (idx: number) => {
  const len = flatGames.value.length
  if (!len) return
  const ids = [idx, (idx + 1) % len, (idx - 1 + len) % len]
  ids.forEach(i => preload(flatGames.value[i]?.bg))
}

onMounted(() => {
  if (flatGames.value.length === 0) return
  currentGameIndex.value = 0
  preloadAround(0)
})

watch(currentGameIndex, (idx) => {
  preloadAround(idx)
})

</script>

<style lang="scss" scoped>
.overlay {
  position: absolute;
  bottom: 0;
  height: 60px;
  width: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  border-radius: 30px;
  overflow: hidden;
}

.v-carousel__item {
  height: 100% !important;
}
</style>
