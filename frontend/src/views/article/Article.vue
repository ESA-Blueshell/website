<template>
  <v-main>
    <top-banner title="news" />
    <div
      class="mx-auto my-10"
      style="max-width: 800px"
    >
      <p class="text-h5">
        {{ news.newsType }}
      </p>
      <p class="text-h4 font-weight-thin">
        {{ news.title }}
      </p>
      <!-- eslint-disable-next-line vue/no-v-html -->
      <p v-html="DOMPurify.sanitize(news.content)" />
      <h5>
        By <b>{{ news.creatorUsername }}</b>,
        {{ news.postedAt ? news.postedAt.slice(0, 10) : "" }}
      </h5>
      <v-btn
        :href="'https://twitter.com/share?text='+news.title+'&url='+thisURL()+'&hashtags='+news.newsType"
        icon="mdi-twitter"
      />
      <v-btn
        :href="'https://www.facebook.com/sharer/sharer.php?u='+thisURL()+'&t='+news.title"
        icon="mdi-facebook"
      />
    </div>
  </v-main>
</template>

<script lang="ts">
import TopBanner from "@/components/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import DOMPurify from "dompurify"

export default {
  components: {TopBanner},
  data() {
    return {
      snackbar: "",
      news: [],
    }
  },
  mounted() {
    this.$http
      .get("news/" + this.$route.params.id)
      .then(response => this.news = response.data)
      .catch(e => $handleNetworkError(e))
  },
  methods: {
    DOMPurify,
    thisURL() {
      return document.URL
    },
  },
}
</script>

<style lang="scss" scoped>

.theme--dark .quote {
  color: #A8FF00;
}

.quote {
  color: gray;
  text-align: center;
  font-style: italic;
  font-size: 1.2rem;
}

.v-application p.text-h5 {
  margin-bottom: 0;
}
</style>
