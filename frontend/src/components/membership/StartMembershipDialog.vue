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
        <v-form
          ref="form"
        >
          <v-row>
            <v-text-field
              v-model="membership.startDate"
              :max="new Date().toISOString()"
              label="Start Date"
              required
              type="date"
            />
          </v-row>
          <v-row>
            <member-type-select v-model="membership.memberType" />
          </v-row>
          <v-row>
            <country-select v-model="membership.country" />
          </v-row>
        </v-form>
      </v-card-text>
      >

      <v-card-actions>
        <v-spacer />
        <v-btn
          :disabled="isSubmitting"
          color="secondary"
          @click="open = false"
        >
          Cancel
        </v-btn>
        <v-btn
          :loading="isSubmitting"
          color="primary"
          @click="confirm()"
        >
          Confirm
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import {computed, type Ref, ref} from "vue"
import {DateTime} from "luxon"
import MemberTypeSelect from "@/components/select/MemberTypeSelect.vue"
import {createMembership, type Membership, MemberType} from "@/lib"
import CountrySelect from "@/components/select/CountrySelect.vue"
import type {VForm} from "vuetify/lib/components"

interface Props {
  modelValue: boolean;
  memberships: Array<Membership>;
  userId: number;
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
}>()

const form: Ref<VForm | undefined> = ref()

// Local v-model proxy
const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
})

// Local state for the dialog
const membership = ref<Membership>({
  startDate: DateTime.now().toISODate(),
  memberType: MemberType.REGULAR,
  userId: props.userId,
  city: "",
  country: "NL",
  incasso: false,
})

const isSubmitting = ref(false)

const confirm = async () => {
  try {
    const validationResult = await form.value?.validate()
    if (!validationResult?.valid) return

    isSubmitting.value = true

    const membershipData: Membership = membership.value
    const response = await createMembership({body: membershipData})

    if (response.data) {

      open.value = false
    }
  } catch (error) {
    console.error("Failed to create membership:", error)
  } finally {
    isSubmitting.value = false
  }
}
</script>
