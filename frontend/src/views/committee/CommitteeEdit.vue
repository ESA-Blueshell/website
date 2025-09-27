<template>
  <v-form
    ref="form"
    v-model="valid"
  >
    <v-text-field
      ref="title"
      v-model="localCommittee.name"
      :rules="[(v: string) => !!v || 'Name is required']"
      label="Committee name"
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
        <v-col cols="8">
          <user-select
            v-if="members"
            v-model="member.user"
            :rules="[
              (u: AdvancedUser) => !!u || 'Select a member',
              (u: AdvancedUser) => !u || localCommittee.members.findIndex((ms: CommitteeMember, idx: number) => ms.user?.id === u.id && idx !== i) === -1 || 'Member already in this committee',
            ]"
            :users="members"
            label="Member name"
          >
            <template #append>
              <v-btn
                icon="mdi-close"
                variant="plain"
                @click="removeMember(i)"
              />
            </template>
          </user-select>
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
import {type AdvancedCommittee, type AdvancedUser, type CommitteeMember, createCommittee, updateCommittee} from "@/lib"
import UserSelect from "@/components/select/UserSelect.vue"

const props = defineProps<{
  committee: {
    members: CommitteeMember[];
    type: AdvancedCommittee,
    required: false,
    default: () => AdvancedCommittee;
  },
  members: {
    type: AdvancedUser[],
    required: true,
  };
}>()

const emit = defineEmits<{
  (e: "submitting"): void;
  (e: "close"): void;
}>()

const valid = ref(false)
const form = ref<VForm | null>(null)

// Create a local copy of the committee to avoid direct prop mutation
const localCommittee = ref<AdvancedCommittee>({
  name: "",
  ...props.committee,
  members: (props.committee?.members ?? []).map((m: CommitteeMember) => ({
    ...m,
    userId: m.userId ?? m.user?.id,
    user: undefined,
  })),
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
    emit("close")
  } catch (error) {
    console.error("Error saving committee:", error)
  }
}
</script>
