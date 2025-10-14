<template>
  <v-main>
    <div
      class="text-body-1 mx-auto mt-16 text-center"
      style="max-width: 800px;"
    >
      <v-img
        :src="$require('@/assets/shellyooooooooooooohhhhhhhhhhh.png')"
        class="mx-auto"
        style="max-width: 800px"
      />
      <p class="mt-10 text-h6 text-sm-h5">
        Uh oh, we made a fucky wucky! Pwease don't report this incident to board otherwise they will
        whip us again. Or do if you're hate sitecie (but then we hate you too >:(
      </p>
      <p class="mt-5 text-h5 text-sm-h3">
        Click <a
          class="text-decoration-none"
          href="/frontend/public"
        >here</a> to go back to the main page!
      </p>
    </div>
  </v-main>
</template>

<script lang="ts">
import {$require} from "@/plugins/require.js"

export default {
  name: "NotFound",
  methods: {$require},
}
</script>

<style lang="scss" scoped>

</style>
