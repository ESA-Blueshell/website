<template>
  <main class="site-main">
    <div
      class="not-found"
      data-testid="not-found"
    >
      <img
        alt=""
        class="not-found__picture"
        :src="$require('@/assets/shellyooooooooooooohhhhhhhhhhh.png')"
      >
      <p class="site-lead not-found__said">
        Uh oh, we made a fucky wucky! Pwease don't report this incident to board otherwise they will
        whip us again. Or do if you're hate sitecie (but then we hate you too >:(
      </p>
      <p class="site-lead not-found__back">
        Click <a
          class="site-link"
          href="/"
        >here</a> to go back to the main page!
      </p>
    </div>
  </main>
</template>

<script lang="ts">
import {$require} from "@/plugins/require.js"

export default {
  name: "NotFound",
  methods: {$require},
}
</script>

<style scoped>
.not-found {
  max-width: 800px;
  margin: 64px auto 0;
  text-align: center;
}

.not-found__picture {
  display: block;
  width: 100%;
  max-width: 800px;
  margin-inline: auto;
}

.not-found__said {
  margin-top: 40px;
}

.not-found__back {
  margin-top: 20px;
}
</style>
