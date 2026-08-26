<template>
  <v-main>
    <top-banner title="My account" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-h3">
          Hello {{ user != null ? user.firstName : "" }}!
        </p>

        <p>
          On this page you can view your account data and edit it below. Fields like your name and e-mail address
          cannot be changed. You should contact board or sitecie on discord if you would like to change any of these
          fields
        </p>
        <p>
          On the "Upcoming events" page you will find all of the events that have been planned. Here, you can sign up
          for events that have it enabled, either by clicking the sign-up checkbox or filling in the sign-up form. (no
          more google forms baybee)
        </p>
        <p v-if="store.getters.isActive">
          With the event manager, you can create and edit an upcoming event for one of the committees you're in. Once an
          event is created it will have to be approved by board {{ store.getters.isBoard ? "(yes, you)" : "" }} before
          it will go public.
        </p>
        <p v-if="store.getters.isBoard">
          Using the committee manager you can manage the committees in the association (duh). You can crate a committee,
          give it a description and add any members to it.
        </p>

        <div
          v-if="user"
          class="mt-10"
        >
          <v-form ref="form">
            <user-form
              v-model="user"
              data-testid="account-user-form"
              :options="{ includeMemberProfile: isMember }"
              show-submit
            />
          </v-form>

          <!-- Its own resource, saved a game at a time, so it stays out of the form's submit. -->
          <game-handles
            v-if="user.id"
            class="mt-8"
            :user-id="user.id"
          />
        </div>
        <v-progress-circular v-else />
      </div>
    </div>
  </v-main>
</template>


<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import UserForm from "@/components/form/UserForm.vue"
import GameHandles from "@/domains/esports/components/GameHandles.vue"
import {findUserById} from "@/services/api"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"

const user = ref<EditableUser>()
const store = useStore()
const isMember = computed<boolean>(() => store.getters.isMember)

onMounted(async () => {
  const login = store.getters.getLogin
  if (!login) return

  try {
    const response = await findUserById({
      path: {
        userId: login.userId,
      },
    })

    if (response.data) {
      user.value = toEditableUser(response.data)
    }
  } catch (e) {
    $handleNetworkError(e)
  }
})
</script>


<style lang="scss" scoped>
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}
</style>
