<script setup lang="ts">
import {ref, onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import type BlogModel from '@/models/BlogModel.ts'
import BlogService from '@/services/BlogService.ts'
import DOMPurify from "dompurify";


const router = useRouter();
// Create an instance of our BlogService
const blogService = new BlogService()

// Reactive reference to hold the single blog data
const blog = ref<BlogModel | null>(null)

// Grab the "id" from the route (assuming your route is set up with :id)
const route = useRoute()
const blogId = String(route.params.id)

// Fetch the blog when component mounts
onMounted(async () => {
  try {
    blog.value = await blogService.getBlog(blogId)
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
        indeterminate
        color="primary"
      />
    </div>
  </v-main>
</template>

<style scoped>

</style>
