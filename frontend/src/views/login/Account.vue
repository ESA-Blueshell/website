<template>
  <v-main>
    <top-banner title="My account"/>
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-h3">
          Hello {{ user != null ? user.firstName : '' }}!
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
          event is created it will have to be approved by board {{ $store.getters.isBoard ? '(yes, you)' : '' }} before
          it will go public.
        </p>
        <p v-if="store.getters.isBoard">
          Using the committee manager you can manage the committees in the association (duh). You can crate a committee,
          give it a description and add any members to it.
        </p>

        <div v-if="user">
          <UserEdit
            :user="user"
            editing
          />
        </div>
        <v-progress-circular v-else/>
      </div>
    </div>
  </v-main>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useStore } from 'vuex';
import TopBanner from "@/components/banners/TopBanner.vue";
import {$handleNetworkError} from "@/plugins/handleNetworkError.js";
import {VPhoneInput} from "v-phone-input";
import {DateTime} from 'luxon';
import UserEdit from "@/components/UserEdit.vue";
import {findUserById, updateUser} from "@/lib/index.js";
import client from "@/plugins/client.js";

// Reactive data
const user = ref(null);
const valid = ref(true);
const submitting = ref(false);
const phone = ref('');
const form = ref(null);

// Store access
const store = useStore();

// Lifecycle hook
onMounted(async () => {
  const login = store.getters.getLogin;

  try {
    const response = await findUserById({
      client,
      path: {
        userId: login.userId
      }
    });

    user.value = response.data;
    if (user.value.dateOfBirth) {
      user.value.dateOfBirth = DateTime.fromISO(user.value.dateOfBirth).toISODate();
    }
  } catch (e) {
    $handleNetworkError(e);
  }
});

// Methods
const copyObject = (obj) => {
  return Object.assign({}, obj);
};

const save = async () => {
  const { valid: isValid } = await form.value.validate();

  if (!isValid) {
    return;
  }

  submitting.value = true;

  try {
    // Send new user object to backend
    const login = store.getters.getLogin;
    await updateUser({
      client,
      path: {
        userId: login.userId
      },
      body: user.value
    });

    submitting.value = false;
  } catch (e) {
    submitting.value = false;
    if (e.response?.status === 400) {
      store.commit('setStatusSnackbarMessage', e.response.data);
    } else {
      $handleNetworkError(e);
    }
  }
};
</script>

<style scoped>
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}
</style>
