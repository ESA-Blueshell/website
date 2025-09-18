<template>
  <v-main>
    <top-banner :title="eventName ? eventName + ' sign-ups' : 'Sign-ups'" />

    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-h5">
          Total signups: {{ responses.length }}
        </p>

        <p class="text-h4">
          Summary
        </p>

        <div
          v-for="(question, i) in signUpForm"
          :key="i"
        >
          <!-- Prompt -->
          <p :class="question.type === 'description' ? 'text-body-1' : 'text-h6 mb0'">
            {{ question.prompt }}
          </p>

          <!-- Open-ended question -->
          <template v-if="question.type === 'open'">
            <p
              v-for="response in responses"
              :key="response.discord"
            >
              <b>{{ response.fullName }}:</b> {{ response.formAnswers[i] }}
            </p>
          </template>

          <!-- Radio question -->
          <template v-else-if="question.type === 'radio'">
            <p
              v-for="response in responses"
              :key="response.discord"
            >
              <b>{{ response.fullName }}:</b> {{ response.formAnswers[i] }}
            </p>
            <v-container>
              <v-row
                v-for="(option, j) in question.options"
                :key="j"
              >
                <v-col>
                  {{ option }}
                </v-col>
                <v-col cols="1">
                  {{ responses.filter(response => response.formAnswers[i] === j).length }}
                </v-col>
              </v-row>
            </v-container>
          </template>

          <!-- Checkbox question -->
          <template v-else-if="question.type === 'checkbox'">
            <p
              v-for="response in responses"
              :key="response.discord"
            >
              <b>{{ response.fullName }}:</b>
              {{ response.formAnswers[i].map((answer: number) => question.options[answer]).join(', ') }}
            </p>
            <v-container>
              <div
                v-for="(option, j) in question.options"
                :key="j + 'key'"
              >
                <v-row
                  :key="j"
                  @click="toggle(i, j)"
                >
                  <v-col>
                    {{ option }}
                  </v-col>
                  <v-col cols="1">
                    {{ responses.filter(response => response.formAnswers[i].includes(j)).length }}
                  </v-col>

                  <!-- Expand transition for listing names under a chosen checkbox -->
                  <v-container class="py-0 pl-16">
                    <v-expand-transition>
                      <div v-if="expandTab.includes(`${i}-${j}`)">
                        <v-row
                          v-for="response in responses.filter(response => response.formAnswers[i].includes(j))"
                          :key="response.discord"
                        >
                          <v-col>
                            {{ response.fullName }}
                          </v-col>
                          <v-col>
                            {{ response.discord }}
                          </v-col>
                        </v-row>
                      </div>
                    </v-expand-transition>
                  </v-container>
                </v-row>
                <v-divider :key="'div' + j" />
              </div>
            </v-container>
          </template>
        </div>
      </div>
    </div>
  </v-main>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import TopBanner from '@/components/banners/TopBanner.vue'
import {
  type EventDto,
  type EventSignUpDto,
  findEventById,
  findEventSignUpsByEventId,
  type FormQuestionDto
} from "@/lib";

// Refs for reactive data
const eventName = ref<string | null>(null)
const signUpForm = ref<FormQuestionDto[]>([])
const responses = ref<EventSignUpDto[]>([])

// This tracks expanded rows for the checkbox question
// We'll store them as `'i-j'` strings
const expandTab = ref<string[]>([])

const route = useRoute()

// Toggle expand/collapse for a given i-j pair
function toggle(i: number, j: number) {
  const key = `${i}-${j}`
  if (expandTab.value.includes(key)) {
    expandTab.value = expandTab.value.filter(item => item !== key)
  } else {
    expandTab.value.push(key)
  }
}

onMounted(async () => {
  try {
    const eventId: number = Number(route.params.id)
    const [eventResp, signupsResp] = await Promise.all([
      findEventById({
        path: {
          id: eventId
        }
      }),
      findEventSignUpsByEventId({
        path: {
          eventId
        }
      })
    ])

    const event: EventDto = eventResp.data ?? {} as EventDto
    const signups: EventSignUpDto[] = signupsResp.data ?? []

    eventName.value = event.title
    signUpForm.value = event.signUpForm ?? []
    responses.value = signups
  } catch (err) {
    // Handle errors as desired
    console.error(err)
  }
})
</script>

<style lang="scss" scoped>
/* Your styles here if needed */
</style>
