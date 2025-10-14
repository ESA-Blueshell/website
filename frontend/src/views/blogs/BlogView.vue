<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import {type Blog, findBlogById} from "@/lib"

// Reactive reference to hold the single blog data
const blog = ref<Blog | null>(null)

// Grab the "id" from the route (assuming your route is set up with :id)
const route = useRoute()
const blogId = Number(route.params.id)

// Fetch the blog when component mounts
onMounted(async () => {
  try {
    const resp = await findBlogById({
      path: {
        id: blogId,
      },
      throwOnError: true,
    })
    blog.value = resp.data!
  } catch (error) {
    console.error(`Error fetching blog with id ${blogId}:`, error)
  }
})

</script>
<template>
  <v-main>
    <div
      v-if="blog"
      class="mx-3 align-center"
    >
      <iframe
        :srcdoc="blog.html"
        frameborder="0"
        style="width: 100%; height: 100vh;"
      />
    </div>
    <div
      v-else
      class="text-center py-10"
    >
      <v-progress-circular
        color="primary"
        indeterminate
      />
    </div>
  </v-main>
</template>

<style lang="scss" scoped>

</style>
