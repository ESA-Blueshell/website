<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import TopBanner from "@/components/banners/TopBanner.vue"
import {type Answer, type Event, type EventSignUp, findEventById, findEventSignUpsByEventId} from "@/lib"

const event = ref<Event>()
const eventSignUps = ref<EventSignUp[]>([])

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
          id: eventId,
        },
      }),
      findEventSignUpsByEventId({
        path: {
          eventId,
        },
      }),
    ])

    eventSignUps.value = signupsResp.data ?? []
    event.value = eventResp.data ?? {} as Event
  } catch (err) {
    // Handle errors as desired
    console.error(err)
  }
})

// function personalDetails(eventSignUp: EventSignUp) {
//   if (eventSignUp.guest) {
//
//   }
//
//   if (eventSignUp.user) {
//
//   }
// }
</script>

<template>
  <v-main>
    <top-banner :title="event?.title ? event?.title + ' sign-ups' : 'Sign-ups'" />

    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-h5">
          Total signups: {{ eventSignUps.length }}
        </p>

        <p class="text-h4">
          Summary
        </p>

        <div
          v-for="(question, i) in event?.signUpForm"
          :key="i"
        >
          <!-- Prompt -->
          <p :class="question.type === 'description' ? 'text-body-1' : 'text-h6 mb0'">
            {{ question.prompt }}
          </p>

          <!-- Open-ended question -->
          <template v-if="question.type === 'open'">
            <p
              v-for="eventSignUp in eventSignUps"
              :key="eventSignUp.discord"
            >
              <b>{{ eventSignUp.fullName }}:</b> {{ eventSignUp.answers[i] }}
            </p>
          </template>

          <!-- Radio question -->
          <template v-else-if="question.type === 'radio'">
            <p
              v-for="eventSignUp in eventSignUps"
              :key="eventSignUp.discord"
            >
              <b>{{ eventSignUp.fullName }}:</b> {{ eventSignUp.answers[i] }}
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
                  {{ eventSignUps.filter((eventSignUp: EventSignUp) => eventSignUp.answers![i] === j).length }}
                </v-col>
              </v-row>
            </v-container>
          </template>

          <!-- Checkbox question -->
          <!-- Checkbox question -->
          <template v-else-if="question.type === 'checkbox'">
            <!-- Per-person list of selected options -->
            <p
              v-for="eventSignUp in eventSignUps"
              :key="eventSignUp.discord"
            >
              <b>{{ eventSignUp.fullName }}:</b>
              {{
                (Array.isArray(eventSignUp.answers?.[i])
                  ? eventSignUp.answers[i]
                  : []
                )
                  .map((answerIndex: number) => question.options?.[answerIndex])
                  .filter((v: Answer) => v != null)
                  .join(", ")
              }}
            </p>

            <!-- Aggregated counts per option with expandable details -->
            <v-container>
              <div
                v-for="(option, j) in question.options"
                :key="`opt-${i}-${j}`"
              >
                <v-row
                  class="cursor-pointer"
                  @click="toggle(i, j)"
                >
                  <v-col>
                    {{ option }}
                  </v-col>
                  <v-col cols="1">
                    {{
                      eventSignUps.filter((su: EventSignUp) =>
                        Array.isArray(su.answers?.[i]) && su.answers[i].includes(j),
                      ).length
                    }}
                  </v-col>
                </v-row>

                <v-container class="py-0 pl-16">
                  <v-expand-transition>
                    <div v-if="expandTab.includes(`${i}-${j}`)">
                      <v-row
                        v-for="su in eventSignUps.filter((su: EventSignUp) =>
                          Array.isArray(su.answers?.[i]) && su.answers[i].includes(j)
                        )"
                        :key="su.discord"
                      >
                        <v-col>
                          {{ su.fullName }}
                        </v-col>
                        <v-col>
                          {{ su.discord }}
                        </v-col>
                      </v-row>
                    </div>
                  </v-expand-transition>
                </v-container>
              </div>
            </v-container>
          </template>

          <!--          <template v-else-if="question.type === 'checkbox'">-->
          <!--            <p-->
          <!--              v-for="eventSignUp in eventSignUps"-->
          <!--              :key="eventSignUp.discord"-->
          <!--            >-->
          <!--              <b>{{ eventSignUp.fullName }}:</b>-->
          <!--              {{ eventSignUp.answers[i].map((answer: number) => question.options[answer]).join(", ") }}-->
          <!--            </p>-->
          <!--            <v-container>-->
          <!--              <div-->
          <!--                v-for="(option, j) in question.options"-->
          <!--                :key="j + 'key'"-->
          <!--              >-->
          <!--                <v-row-->
          <!--                  :key="j"-->
          <!--                  @click="toggle(i, j)"-->
          <!--                >-->
          <!--                  <v-col>-->
          <!--                    {{ option }}-->
          <!--                  </v-col>-->
          <!--                  <v-col cols="1">-->
          <!--                    {{ eventSignUps.filter((eventSignUp: EventSignUp) => eventSignUp.answers[i].includes(j)).length }}-->
          <!--                  </v-col>-->

          <!--                  &lt;!&ndash; Expand transition for listing names under a chosen checkbox &ndash;&gt;-->
          <!--                  <v-container class="py-0 pl-16">-->
          <!--                    <v-expand-transition>-->
          <!--                      <div v-if="expandTab.includes(`${i}-${j}`)">-->
          <!--                        <v-row-->
          <!--                          v-for="eventSignUp in eventSignUps.filter((eventSignUp: EventSignUp) => eventSignUp.answers[i].includes(j))"-->
          <!--                          :key="eventSignUp.discord"-->
          <!--                        >-->
          <!--                          <v-col>-->
          <!--                            {{ eventSignUp.fullName }}-->
          <!--                          </v-col>-->
          <!--                          <v-col>-->
          <!--                            {{ eventSignUp.discord }}-->
          <!--                          </v-col>-->
          <!--                        </v-row>-->
          <!--                      </div>-->
          <!--                    </v-expand-transition>-->
          <!--                  </v-container>-->
          <!--                </v-row>-->
          <!--                <v-divider :key="'div' + j" />-->
          <!--              </div>-->
          <!--            </v-container>-->
          <!--          </template>-->
        </div>
      </div>
    </div>
  </v-main>
</template>


<style lang="scss" scoped>
/* Your styles here if needed */
</style>
