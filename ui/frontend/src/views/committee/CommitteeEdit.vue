<template>
  <v-form
    ref="form"
    v-model="valid"
  >
    <v-text-field
      ref="title"
      v-model="localCommittee.name"
      :rules="[v => !!v || 'Name is required']"
      label="CommitteeModel name"
      required
    />

    <v-textarea
      ref="description"
      v-model="localCommittee.description"
      :rules="[v => !!v || 'Description is required']"
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
          <v-autocomplete
            v-if="members"
            v-model="member.user"
            :item-title="user => user.discord ? `${user.fullName} (${user.discord})` : user.fullName"
            :items="members"
            :rules="[
              v => !!v || 'Select a member',
              v => (!!v && localCommittee.members.filter(ms => ms.user?.username === v.username).length === 1) || 'Member can\'t be in committee twice'
            ]"
            hide-details="auto"
            auto-select-first
            clearable
            hide-no-data
            label="Member name"
            return-object
          >
            <template #append>
              <v-btn
                icon="mdi-close"
                variant="plain"
                @click="removeMember(i)"
              />
            </template>
          </v-autocomplete>
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
import { ref, onMounted } from 'vue';
import type { VForm } from 'vuetify/components';
import type {CommitteeModel, CommitteeMemberModel, SimpleUserModel} from '@/models';
import { UserService, CommitteeService } from '@/services';

const props = defineProps<{
  committee: {
    members: CommitteeMemberModel[];
    type: CommitteeModel,
    required: false,
  };
}>();

const emit = defineEmits<{
  (e: 'submitting'): void;
  (e: 'close'): void;
}>();

const valid = ref(false);
const members = ref<SimpleUserModel[]>([]);
const form = ref<VForm | null>(null);
const userService = new UserService();
const committeeService = new CommitteeService();

// Create a local copy of the committee to avoid direct prop mutation
const localCommittee = ref<CommitteeModel>({
  ...props.committee,
  members: props.committee?.members ? [...props.committee.members.map(m => ({ ...m }))] : [],
  type: 'committee'
});

// Fetch members on mount
onMounted(async () => {
  members.value = await userService.getUsers(true);
});

const addMember = () => {
  localCommittee.value.members.push({
    role: '',
    user: {} as SimpleUserModel,
    type: 'committeeMember',
    committeeId: localCommittee.value.id
  });
};

const removeMember = (index: number) => {
  localCommittee.value.members.splice(index, 1);
};

const submit = async () => {
  const { valid: formValid } = await form.value?.validate() ?? { valid: false };
  if (!formValid) return;

  emit('submitting');

  try {
    if (localCommittee.value.id) {
      await committeeService.updateCommittee(localCommittee.value.id, localCommittee.value);
    } else {
      await committeeService.createCommittee(localCommittee.value);
    }
    emit('close');
  } catch (error) {
    console.error('Error saving committee:', error);
  }
};
</script>
