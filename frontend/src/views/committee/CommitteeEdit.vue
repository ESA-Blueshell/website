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
      label="Description"
      variant="outlined"
      hide-details
      required
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
            :users="members"
            :rules="[
              (u: SimpleUser) => !!u || 'Select a member',
              (u: SimpleUser) => !u || localCommittee.members.findIndex((ms: CommitteeMember, idx: number) => ms.user?.id === u.id && idx !== i) === -1 || 'Member already in this committee',
            ]"
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
      variant="outlined"
      class="mb-4"
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

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import type {VForm} from 'vuetify/components';
import {
  type AdvancedCommittee,
  type CommitteeMember,
  createCommittee,
  findUsers,
  type SimpleUser,
  updateCommittee
} from "@/lib";
import UserSelect from "@/components/select/UserSelect.vue";

const props = defineProps<{
  committee: {
    members: CommitteeMember[];
    type: AdvancedCommittee,
    required: false,
    default: () => AdvancedCommittee;
  };
}>();

const emit = defineEmits<{
  (e: 'submitting'): void;
  (e: 'close'): void;
}>();

const valid = ref(false);
const members = ref<SimpleUser[]>([]);
const form = ref<VForm | null>(null);

// Create a local copy of the committee to avoid direct prop mutation
const localCommittee = ref<AdvancedCommittee>({
  name: '',
  ...props.committee,
  members: (props.committee?.members ?? []).map((m: CommitteeMember) => ({
    ...m,
    userId: m.userId ?? m.user?.id,
    user: undefined
  }))
});

// Fetch members on mount
onMounted(async () => {
  const resp = await findUsers({
    query: {
      isMember: true
    }
  });
  members.value = resp.data ?? [];
});

const addMember = () => {
  localCommittee.value.members.push({
    role: '',
    committeeId: localCommittee.value.id,
    userId: undefined,
  });
};

const removeMember = (index: number) => {
  localCommittee.value.members.splice(index, 1);
};

const submit = async () => {
  const {valid: formValid} = await form.value?.validate() ?? {valid: false};
  if (!formValid) return;

  emit('submitting');

  try {
    if (localCommittee.value.id) {
      await updateCommittee({
        body: localCommittee.value,
        path: {
          id: localCommittee.value.id
        }
      })
    } else {
      await createCommittee({
        body: localCommittee.value,
      })
    }
    emit('close');
  } catch (error) {
    console.error('Error saving committee:', error);
  }
};
</script>
