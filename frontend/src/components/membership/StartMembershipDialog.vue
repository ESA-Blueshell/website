<template>
  <v-dialog
    v-model="open"
    max-width="500"
  >
    <v-card>
      <v-card-title class="text-h5">
        Start Membership
      </v-card-title>
      <v-card-text>
        <v-row>
          <v-text-field
            v-model="form.startDate"
            :max="new Date().toISOString()"
            label="Start Date"
            type="date"
            required
          />
        </v-row>
        <v-row>
          <member-type-select v-model="form.memberType"/>
        </v-row>
        <v-row>
          <country-select v-model="form.country"/>
        </v-row>
      </v-card-text>

      <v-card-actions>
        <v-spacer/>
        <v-btn
          color="secondary"
          :disabled="isSubmitting"
          @click="open = false"
        >
          Cancel
        </v-btn>
        <v-btn
          color="primary"
          :loading="isSubmitting"
          @click="confirm()"
        >
          Confirm
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {DateTime} from 'luxon';
import MemberTypeSelect from '@/components/select/MemberTypeSelect.vue';
import {createMembership, type Membership, MemberType,} from '@/lib';
import CountrySelect from "@/components/select/CountrySelect.vue";

interface Props {
  /** Control the dialog from the parent with v-model */
  modelValue: boolean;
  /** User ID to create the membership for */
  userId: number;
}


const props = defineProps<Props>();
const emit = defineEmits<{
  /** v-model updater */
  (e: 'update:modelValue', value: boolean): void;
  /** Fires when a membership is successfully created */
  (e: 'started', membership: Membership): void;
}>();

// Local v-model proxy
const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
});

// Local state for the dialog
const form = ref<Membership>({
  startDate: DateTime.now().toISODate(),
  memberType: MemberType.REGULAR,
  userId: props.userId,
  city: '',
  country: 'NL',
  incasso: false,
});

const isSubmitting = ref(false);

const confirm = async () => {
  try {
    isSubmitting.value = true;

    const membershipData: Membership = form.value

    const response = await createMembership({body: membershipData});

    if (response.data) {
      emit('started', response.data);
      open.value = false;
    }
  } catch (error) {
    console.error('Failed to create membership:', error);
  } finally {
    isSubmitting.value = false;
  }
};
</script>
