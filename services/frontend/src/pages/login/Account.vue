<template>
  <account-frame heading="Account">
    <div class="site-gutter">
      <div
        class="site-column"
        style="max-width: 800px"
      >
        <p class="account__hello">
          Hello {{ user != null ? user.firstName : "" }}!
        </p>

        <pending-membership
          v-if="user"
          class="account__pending"
        />

        <div
          v-if="user"
          class="account__details"
        >
          <user-form
            v-model="user"
            data-testid="account-user-form"
            :options="{ includeMemberProfile: true, memberProfileRequired: isMember }"
            show-submit
          />
        </div>
        <loading-spinner v-else />
      </div>
    </div>
  </account-frame>
</template>


<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import UserForm from "@/components/form/UserForm.vue"
import PendingMembership from "@/components/account/PendingMembership.vue"
import LoadingSpinner from "@/components/island/LoadingSpinner.vue"
import {readUser} from "@/domains/user"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"

const user = ref<EditableUser>()
const store = useStore()
const isMember = computed<boolean>(() => store.getters.isMember)

onMounted(async () => {
  const login = store.getters.getLogin
  if (!login) return

  try {
    const found = await readUser(login.userId)

    if (found) {
      user.value = toEditableUser(found)
    }
  } catch (e) {
    $handleNetworkError(e)
  }
})
</script>


<style lang="scss" scoped>

.account__hello {
  font-size: 26px;
  line-height: 1.3;
}

.account__pending {
  margin-top: 24px;
}

.account__details {
  margin-top: 40px;
}
</style>
