<script setup>
import {useRoute, useRouter} from "vue-router";
import TopBanner from "@/components/banners/TopBanner.vue";
import axios from "axios";
import {ref} from "vue";
import EventListItem from "@/components/events/EventListItem.vue";
import EventSignUpForm from "@/components/events/EventSignUpForm.vue";
import {useStore} from "vuex";
import {$handleNetworkError} from "@/plugins/handleNetworkError";
import {$goto} from "@/plugins/goto";

const route = useRoute()
const router = useRouter()
const store = useStore()

const accessToken = route.params.accessToken

const signUp = ref(null)
const event = ref(null)
const submitting = ref(false)
const deleting = ref(false)

axios.get(`events/signups/byAccessToken/${accessToken}`)
  .then(response => {
    signUp.value = response.data

    axios.get(`events/${signUp.value.event}`)
      .then(response => event.value = response.data)
  })
  .catch(e => e.response?.status === 404
    ? router.push({name: 'NotFound'})
    : $handleNetworkError(e))


async function submitForm({answers}) {
  submitting.value = true

  const response = await axios.put(`events/${signUp.value.event}/signups/${signUp.value.id}`, JSON.stringify(answers.value), {headers: {'Content-Type': 'text/plain'}})

  submitting.value = false

  store.commit('setStatusSnackbarMessage', 'Sign-up updated. See you at the event! :)')
}

async function deleteSignUp() {
  deleting.value = true

  try {
    await axios.delete(`events/${signUp.value.event}/signups/${signUp.value.id}`)

    deleting.value = false
    store.commit('setStatusSnackbarMessage', 'Sign-up cancelled. You can always sign-up again if you change your mind :)')

    $goto('/events')
  } catch (e) {
    $handleNetworkError(e)
  }
}


</script>

<template>
  <v-main>
    <top-banner title="Edit sign-up" />
    <div
      class="mx-auto my-10"
      style="max-width: 800px"
    >
      <div class="mx-3">
        <v-expand-transition>
          <div v-if="event">
            <v-list>
              <event-list-item :event="event" />
            </v-list>

            <p class="mt-8">
              Here you can edit your sign-up for this event. If you have any questions, feel free to ask in the event's
              channel on Discord. If you can't come anymore or you want to cancel your sign-up for any reason, just
              click
              the "Cancel sign-up" button at the bottom of the page.
            </p>

            <event-sign-up-form
              v-if="signUp?.formAnswers"
              :event="event"
              :answers-string="signUp.formAnswers"
              :button-loading="submitting"
              :show-guest-form="false"
              class="accent-bordered mt-8"
              @submit="submitForm"
            />

            <v-btn
              class="mt-8"
              block
              color="error"
              variant="outlined"
            >
              Cancel sign-up

              <v-dialog
                activator="parent"
                width="auto"
              >
                <template #default="{ isActive }">
                  <v-card
                    title="Cancel Sign-up"
                    text="Are you sure you want to cancel your sign-up for this event?"
                  >
                    <template #actions>
                      <v-spacer />
                      <v-btn
                        class="ml-auto"
                        variant="text"
                        @click="isActive.value = false"
                      >
                        No
                      </v-btn>
                      <v-btn
                        class="ml-auto"
                        color="error"
                        variant="text"
                        @click="deleteSignUp();isActive.value = false"
                      >
                        Yes
                      </v-btn>
                    </template>
                  </v-card>
                </template>
              </v-dialog>
            </v-btn>
          </div>
        </v-expand-transition>
      </div>
    </div>
  </v-main>
</template>

<style scoped lang="scss">
@use '@/styles/settings';

.accent-bordered {
  border: 1px solid rgb(var(--v-theme-accent));
  border-radius: settings.$border-radius-root;
  padding: 16px;
}
</style>
