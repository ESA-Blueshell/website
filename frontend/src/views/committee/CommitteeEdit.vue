<template>
  <v-form
    ref="form"
    v-model="valid"
  >
    <v-text-field
      ref="title"
      v-model="localCommittee.name"
      :rules="[(v: string) => !!v || 'Name is required']"
      label="Name"
      required
    />

    <v-textarea
      ref="description"
      v-model="localCommittee.description"
      :rules="[(v: string) => !!v || 'Description is required']"
      hide-details
      label="Description"
      required
      variant="outlined"
    />

    <v-container>
      <v-row
        v-for="(member, i) in localCommittee.members"
        :key="i"
        dense
      >
        <v-col cols="4">
          <v-text-field
            v-model="member.role"
            hide-details="auto"
            label="Role"
          />
        </v-col>
        <v-col cols="7">
          <user-select
            v-if="users"
            v-model="member.userId"
            :rules="[
              (user: AdvancedUser) => !!user || 'Select a user',
              (user: AdvancedUser) => !user || user.roles?.includes(Role.MEMBER) || 'Committee members must be members of the association',
              (user: AdvancedUser) => !user || localCommittee.members.findIndex((ms: CommitteeMember, idx: number) => ms.userId === user.id && idx !== i) === -1 || 'Member already in this committee',
            ]"
            :users="users"
            label="Member name"
          />
        </v-col>
        <v-col cols="1">
          <v-btn
            icon="mdi-close"
            variant="plain"
            @click="removeMember(i)"
          />
        </v-col>
      </v-row>
    </v-container>

    <v-btn
      block
      class="mb-4"
      variant="outlined"
      @click="addMember"
    >
      Add member
    </v-btn>

    <v-btn
      block
      @click="submit"
    >
      Save committee
    </v-btn>
  </v-form>
</template>

<script lang="ts" setup>
import {ref} from "vue"
import type {VForm} from "vuetify/components"
import {
  type AdvancedCommittee,
  type AdvancedUser,
  type CommitteeMember,
  createCommittee,
  Role,
  updateCommittee,
} from "@/lib"
import UserSelect from "@/components/select/UserSelect.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const props = defineProps<{
  committee: {
    members: CommitteeMember[];
    type: AdvancedCommittee,
    required: false,
    default: () => AdvancedCommittee;
  },
  users: {
    type: AdvancedUser[],
    required: true,
  };
}>()

const emit = defineEmits<{
  (e: "submitting"): void;
  (e: "success", ok: boolean): void;
}>()

const valid = ref(false)
const form = ref<VForm | null>(null)

// Create a local copy of the committee to avoid direct prop mutation
const localCommittee = ref<AdvancedCommittee>({
  name: "",
  description: "",
  ...props.committee,
  members: (props.committee?.members ?? []),
})

const addMember = () => {
  localCommittee.value.members!.push({
    role: "",
    committeeId: localCommittee.value.id as number,
    userId: 0,
  })
}

const removeMember = (index: number) => {
  localCommittee.value.members!.splice(index, 1)
}

const submit = async () => {
  const {valid: formValid} = await form.value?.validate() ?? {valid: false}
  if (!formValid) return

  emit("submitting")

  try {
    if (localCommittee.value.id) {
      await updateCommittee({
        body: localCommittee.value,
        path: {
          id: localCommittee.value.id,
        },
      })
    } else {
      await createCommittee({
        body: localCommittee.value,
      })
    }
    emit("success", true)
  } catch (error) {
    console.error("Error saving committee:", error)
    $handleNetworkError(error)
    emit("success", false)
  }
}
</script>
