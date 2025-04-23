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
        {{ news.postedAt ? news.postedAt.slice(0, 10) : '' }}
      </h5>
      <v-btn
        icon="mdi-twitter"
        :href="'https://twitter.com/share?text='+news.title+'&url='+thisURL()+'&hashtags='+news.newsType"
      />
      <v-btn
        icon="mdi-facebook"
        :href="'https://www.facebook.com/sharer/sharer.php?u='+thisURL()+'&t='+news.title"
      />
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$handleNetworkError} from "@/plugins/handleNetworkError";
import DOMPurify from "dompurify";

export default {
  components: {TopBanner},
  data() {
    return {
      snackbar: "",
      news: []
    }
  },
  mounted() {
    this.$http
      .get('news/' + this.$route.params.id)
      .then(response => this.news = response.data)
      .catch(e => $handleNetworkError(e))
  },
  methods: {
    DOMPurify,
    thisURL() {
      return document.URL
    }
  }
}
</script>

<style>

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
