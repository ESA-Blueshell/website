<template>
  <v-main>
    <div
      class="text-body-1 mx-auto mt-16 text-center"
      style="max-width: 800px;"
    >
      <v-img
        class="mx-auto"
        :src="$require('@/assets/shellyooooooooooooohhhhhhhhhhh.png')"
        style="max-width: 800px"
      />
      <p class="mt-10 text-h6 text-sm-h5">
        Uh oh, we made a fucky wucky! Pwease don't report this incident to board otherwise they will
        whip us again. Or do if you're hate sitecie (but then we hate you too >:(
      </p>
      <p class="mt-5 text-h5 text-sm-h3">
        Click <a
          href="/"
          class="text-decoration-none"
        >here</a> to go back to the main page!
      </p>
    </div>
  </v-main>
</template>

<script>
import {$require} from "@/plugins/require";

export default {
  name: "NotFound",
  methods: {$require}
}
</script>

<style scoped>

</style>
