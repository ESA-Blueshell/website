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
            class="text-decoration-none"
            href="mailto:internal-affairs@blueshell.utwente.nl"
            target="_blank"
          >internal-affairs@blueshell.utwente.nl</a>
          . You could also ask us on Discord or in person at one of the events.
        </p>
        <p class="text-body-1">
          Do you have a great idea for an event or a new committee, then be sure to contact us!
        </p>
      </div>

      <div
        v-if="loading"
        class="mx-3 my-6"
      >
        <v-progress-circular indeterminate />
      </div>

      <div
        v-else-if="!committees.length"
        class="mx-3 my-6"
      >
        <p class="text-body-1">
          No committees found.
        </p>
      </div>

      <v-expansion-panels
        v-else
        variant="accordion"
      >
        <v-expansion-panel
          v-for="committee in committees"
          :key="committee.id ?? committee.name"
        >
          <v-expansion-panel-title class="text-h5 font-weight-light">
            {{ committee.name }}
          </v-expansion-panel-title>
          <v-expansion-panel-text>
            <p v-html="$markdownToHtml(committee.description)" />
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>
    </div>
  </v-main>
</template>

<script setup lang="ts">
import {onMounted, ref} from "vue"
import TopBanner from "@/components/banners/TopBanner.vue"

import {$handleNetworkError} from "@/plugins/handleNetworkError.js"

import type {SimpleCommittee} from "@/lib"
import {findCommittees} from "@/lib"
import $markdownToHtml from "@/plugins/markdownToHtml.ts"

const committees = ref<SimpleCommittee[]>([])
const loading = ref<boolean>(false)

onMounted(async () => {
  loading.value = true
  try {
    const {data} = await findCommittees()
    committees.value = (data ?? []) as SimpleCommittee[]
  } catch (e) {
    $handleNetworkError(e)
    committees.value = []
  } finally {
    loading.value = false
  }
})
</script>
