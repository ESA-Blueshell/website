<script lang="ts" setup>
import {onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {DateTime} from "luxon"
import {type BlogResponse, listBlogs} from "@/domains/blogs"

const blogs = ref<BlogResponse[]>([])
const failedToLoad = ref(false)

onMounted(async () => {
  try {
    blogs.value = await listBlogs()
  } catch (error) {
    failedToLoad.value = true
    console.error("Error fetching blog list:", error)
  }
})
</script>

<template>
  <v-main>
    <top-banner title="Newsletters" />
    <div
      class="mx-auto my-10"
      style="max-width: 900px"
    >
      <div class="mx-3">
        <p class="text-body-1">
          Welcome to our newsletters page! Here you'll find the latest updates, insights, and stories from our
          community.
          Click any newsletter to read the full article.
        </p>
      </div>

      <div
        v-if="failedToLoad"
        class="text-center py-10"
      >
        <p class="text-body-1">
          Failed to load newsletters.
        </p>
      </div>

      <ul
        v-else
        class="blogs"
      >
        <li
          v-for="blog in blogs"
          :key="blog.id"
        >
          <router-link
            class="blogs__row"
            :data-testid="`blog-row-${blog.id}`"
            :to="`/blogs/${blog.id}`"
          >
            <span class="blogs__title">{{ blog.title }}</span>
            <span class="blogs__date">{{ DateTime.fromISO(blog.publishedAt as string).toLocaleString() }}</span>
          </router-link>
        </li>
      </ul>
    </div>
  </v-main>
</template>

<style scoped>
.blogs {
  margin: 1rem 0 0;
  padding: 0;
  list-style: none;
}

.blogs__row {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.8rem 0.75rem;
  color: inherit;
  text-decoration: none;
  border-top: 1px solid var(--color-hairline);
}

.blogs__row:hover,
.blogs__row:focus-visible {
  background-color: var(--color-raised);
}

.blogs__title {
  font-weight: 600;
}

.blogs__date {
  font-size: 0.85rem;
  color: var(--color-ash);
}
</style>
