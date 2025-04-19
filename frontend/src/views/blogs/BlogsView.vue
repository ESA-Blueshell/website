<script setup lang="ts">
import {ref, onMounted} from 'vue';
import TopBanner from '@/components/banners/TopBanner.vue';
import BlogService from '@/services/BlogService.ts';
import $markdownToHtml from '@/plugins/markdownToHtml.ts';
import type BlogModel from "@/models/BlogModel.ts";
import DOMPurify from "dompurify";
import { useRouter } from 'vue-router'
import {DateTime} from "luxon";

const router = useRouter()

const blogs = ref<BlogModel[]>([]);
const blogService = new BlogService();

onMounted(async () => {
  try {
    blogs.value = await blogService.getBlogs();
  } catch (error) {
    console.error('Error fetching blog list:', error);
  }
});

const navigateToBlog = (blogId: number) => {
  router.push(`/blogs/${blogId}`)
}
</script>

<template>
  <v-main>
    <top-banner title="Newsletters" />
    <div
      class="mx-auto my-10"
      style="max-width: 900px"
    >
      <!-- Updated Introduction Section -->
      <div class="mx-3">
        <p class="text-body-1">
          Welcome to our newsletters page! Here you'll find the latest updates, insights, and stories from our
          community.
          Click any newsletter to read the full article.
        </p>
      </div>

      <!-- Newsletters List -->
      <v-list class="pt-0">
        <v-list-item
          v-for="blog in blogs"
          :key="blog.id"
          :value="blog.id"
          class="px-0"
          ripple
          @click="navigateToBlog(blog.id as string)"
        >
          <v-divider />
          <div class="flex-grow-1 ml-3 my-1">
            <v-list-item-title>
              {{ blog.title }}
            </v-list-item-title>
            <v-list-item-subtitle>
              {{ DateTime.fromISO(blog.publishedAt).toLocaleString("en-GB") }}
            </v-list-item-subtitle>
          </div>
        </v-list-item>
      </v-list>
    </div>
  </v-main>
</template>

<style scoped>
</style>
