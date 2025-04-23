<template>
  <v-main>
    <top-banner title="Committees" />
    <div
      class="mx-auto my-10"
      style="max-width: 800px"
    >
      <div class="mx-3">
        <p class="text-body-1">
          Would you like to make the most of your student life, and experience what it is to work
          together with other students to make great things happen? If so, then perhaps joining a
          committee is something for you! Committees are groups of students that work together to
          organize events or provide services for the association, while also having a lot of fun
          and getting some professional experience.
        </p>
        <p class="text-body-1">
          If you would like to join a meeting to see if we are something for you, or if you have a
          question, feel free to contact the board at
          <a
            href="mailto:internal-affairs@blueshell.utwente.nl"
            target="_blank"
            class="text-decoration-none"
          >internal-affairs@blueshell.utwente.nl</a>
          . You could also ask us on Discord or in person at one of the events.
        </p>
        <p class="text-body-1">
          Do you have a great idea for an event or a new committee, then be sure to contact us!
        </p>
      </div>
      <v-expansion-panels variant="accordion">
        <v-expansion-panel
          v-for="committee in committees"
          :key="committee.name"
        >
          <v-expansion-panel-title class="text-h5 font-weight-light">
            {{ committee.name }}
          </v-expansion-panel-title>
          <v-expansion-panel-text>
            <!-- eslint-disable-next-line vue/no-v-html -->
            <p v-html="$markdownToHtml(committee.description)" />
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>
    </div>
  </v-main>
</template>
<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$handleNetworkError} from "@/plugins/handleNetworkError";
import $markdownToHtml from "@/plugins/markdownToHtml.ts";

export default {
  components: {TopBanner: TopBanner},
  data: () => {
    return {
      committees: []
    }
  },
  mounted() {

    this.$http.get('committees')
      .then(response => {
        let data = response.data;
        if (data.length > 0) {
          this.committees = data
        } else {
          this.noCommittees = true
        }
      })
      .catch(e => $handleNetworkError(e))
  },
  methods: {$markdownToHtml}
}
</script>

