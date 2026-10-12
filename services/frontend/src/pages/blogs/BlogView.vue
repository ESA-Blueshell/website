<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import {type BlogResponse, readBlog} from "@/domains/blogs"
import {tabTitle} from "@/plugins/tabTitle"
import LoadingSpinner from "@/components/island/LoadingSpinner.vue"

// Reactive reference to hold the single blog data
const blog = ref<BlogResponse | null>(null)
const loading = ref(true)
const notFound = ref(false)

// Grab the "id" from the route (assuming your route is set up with :id)
const route = useRoute()
const blogId = Number(route.params.id)

// Fetch the blog when component mounts
onMounted(async () => {
  loading.value = true
  notFound.value = false
  try {
    blog.value = await readBlog(blogId)
    notFound.value = blog.value === null
    if (blog.value) document.title = tabTitle(blog.value.title)
  } catch (error) {
    console.error(`Error fetching blog with id ${blogId}:`, error)
  } finally {
    loading.value = false
  }
})

</script>
<template>
  <main class="site-main">
    <div
      v-if="blog"
      class="site-gutter"
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
      class="site-empty"
    >
      <loading-spinner class="blog-loading" />
    </div>
    <div
      v-else-if="notFound"
      class="site-empty"
    >
      <p>
        Blog not found.
      </p>
    </div>
    <div
      v-else
      class="site-empty"
    >
      <p>
        Failed to load blog.
      </p>
    </div>
  </main>
</template>

<style scoped>
.blog-loading {
  color: var(--color-brand-ink);
}
</style>
