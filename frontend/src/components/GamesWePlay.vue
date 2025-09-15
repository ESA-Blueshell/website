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
      :key="category.categoryName"
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
          :key="game.title"
          class="mx-3 my-2 pa-2"
          style="border-radius: 10px;z-index: 3;transition: .3s cubic-bezier(.25,.8,.5,1) !important;"
          :class="{ 'elevation-8': (showPopup || hoverCarousel) && currentGame!==null && i===currentGame.y && j===currentGame.x }"
          :style="{
            cursor: game.esportsLink ? 'pointer' : 'auto', 'background-color': !($vuetify.theme.global.current.dark && (!showPopup && !hoverCarousel)) && ((!showPopup && !hoverCarousel) || (currentGame!==null && i===currentGame.y && j===currentGame.x )) ? '#F5F5F5aa' : '#F5F5F544',
            width: $vuetify.display.smAndUp ? '85px' : '70px',
            height: $vuetify.display.smAndUp ? '85px' : '70px',
          }"

          @click="$goto(game.esportsLink)"
          @mouseenter="hover(i,j)"
          @mouseleave="unhover"
        >
          <v-lazy
            :width="$vuetify.display.smAndUp ? '69px' : '54px'"
            :height="$vuetify.display.smAndUp ? '69px' : '54px'"
            :options="{'threshold':0.1}"
          >
            <v-img :src="game.icon" />
          </v-lazy>
        </div>
      </div>
    </div>

    <!--
      Black div covering the whole component to serve as the background.
      If this wasn't here, the background would be white before the images load in so that would look shit.
    -->
    <div
      style="top:0;left:0;width:100%;height:100%;position: absolute;z-index: 0;background: rgba(0, 0, 0, 1)"
    />


    <!--
      Transparent black div covering the whole component, used to darken the image.
    -->
    <div
      style="top:0;left:0;width:100%;height:100%;position: absolute;z-index: 2;background: rgba(0, 0, 0, 0.6)"
    />

    <!--
      v-carousel covering the whole component to have a fancy image as the background. https://vuetifyjs.com/en/components/carousels/
      The carousel is v-modeled with currentGameIndex, which is set by the "buttons" written above.
    -->
    <div style="top:0;left:0;width:100%;height:100%;position: absolute;z-index: 1;">
      <v-carousel
        id="carousel"
        v-model="currentGameIndex"
        :show-arrows="false"
        hide-delimiters
        theme="light"
        style="height: 100%;z-index: 1;"
      >
        <v-carousel-item
          v-for="game in games.map(it => it.titles).flat()"
          :key="game.title"
          style="z-index: 1;"
          @mouseenter="hover(null,null)"
          @mouseleave="unhover"
        >
          <v-sheet
            height="100%"
            style="z-index: 1;background: rgba(0, 0, 0, 1)"
          >
            <v-lazy :options="{'threshold':0.1}">
              <v-img
                :src="game.bg"
                cover
                style="position: absolute;top: 0;height: 100%;width: 100%;z-index: 1;filter: blur(3px);-webkit-filter: blur(3px);"
              />
            </v-lazy>
          </v-sheet>
        </v-carousel-item>
      </v-carousel>
    </div>
  </div>
</template>

<script>
import {$goto} from "@/plugins/goto";

export default {
  name: "GamesWePlay",
  props: ["games"],
  data: () => ({
    showPopup: 0,
    currentGame: null,
    currentGameIndex: null,
    hoverCarousel: false
  }),
  methods: {
    $goto,
    hover(i, j) {
      setTimeout(() => {
        this.showPopup++;
        if (i !== null && j !== null) {
          this.currentGame = {y: i, x: j};

          let newIndex = 0;
          for (let k = 0; k < i; k++) {
            newIndex += this.games[k].titles.length;
          }
          newIndex += j;
          this.currentGameIndex = newIndex;
        }
      }, 1)
    },
    unhover() {
      setTimeout(() => {
        this.showPopup--;
      }, 1000)
    },
  }
}
</script>

<style lang="scss">
.overlay {
  position: absolute;
  bottom: 0;
  height: 60px;
  width: 100%;
  //z-index: 500;
  background-color: rgba(0, 0, 0, 0.5);
  border-radius: 30px;
  overflow: hidden;
}

// hackerman
.v-carousel__item {
  height: 100% !important;
}
</style>
