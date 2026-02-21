<script lang="ts" setup>
import {onMounted, ref} from "vue"
import axios from "axios"
import {useRoute} from "vue-router"
import {type BlogResponse, findBlogById} from "@/services/api"

// Reactive reference to hold the single blog data
const blog = ref<BlogResponse | null>(null)
const loading = ref(true)
const notFound = ref(false)
const failedToLoad = ref(false)

// Grab the "id" from the route (assuming your route is set up with :id)
const route = useRoute()
const blogId = Number(route.params.id)

// Fetch the blog when component mounts
onMounted(async () => {
  loading.value = true
  notFound.value = false
  failedToLoad.value = false
  try {
    const resp = await findBlogById({
      path: {
        id: blogId,
      },
      throwOnError: true,
    })
    blog.value = resp.data ?? null
    if (!blog.value) {
      notFound.value = true
    }
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      notFound.value = true
    } else {
      failedToLoad.value = true
    }
    console.error(`Error fetching blog with id ${blogId}:`, error)
  } finally {
    loading.value = false
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
        referrerpolicy="no-referrer"
        sandbox=""
        style="width: 100%; height: 100vh;"
        title="Blog content"
      />
    </div>
    <div
      v-else-if="loading"
      class="text-center py-10"
    >
      <v-progress-circular
        color="primary"
        indeterminate
      />
    </div>
    <div
      v-else-if="notFound"
      class="text-center py-10"
    >
      <p class="text-body-1">
        Blog not found.
      </p>
    </div>
    <div
      v-else-if="failedToLoad"
      class="text-center py-10"
    >
      <p class="text-body-1">
        Failed to load blog.
      </p>
    </div>
  </v-main>
</template>
