<template>
  <div>
    <v-list-item>
      <div
        class="d-flex justify-space-between align-center"
        style="width: 100%;"
      >
        <div class="flex-grow-1">
          <v-list-item-title>{{ user.fullName }}</v-list-item-title>
          <v-list-item-subtitle>{{ user.username }}</v-list-item-subtitle>
        </div>

        <div
          class="d-flex align-center"
          style="flex-shrink: 0;"
        >
          <div class="d-flex align-center mr-4">
            <v-chip
              v-if="!!user?.roles?.length"
              :color="user.roles.includes('MEMBER') ? 'primary' : 'grey'"
              class="mr-3 d-flex justify-center align-center text-capitalize"
              size="small"
              style="width: 60px"
              variant="flat"
            >
              {{ user.roles.includes("MEMBER") ? "Member" : "User" }}
            </v-chip>

            <v-btn
              :disabled="disabled || saving"
              :loading="saving"
              size="small"
              variant="tonal"
              @click.stop="hasContribution ? unmarkPaid() : markPaid()"
            >
              {{ hasContribution ? "Mark unpaid" : "Mark paid" }}
            </v-btn>
          </div>
        </div>
      </div>
    </v-list-item>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {type AdvancedUser, type Contribution, createContribution, deleteContribution} from "@/lib"

const props = withDefaults(defineProps<{
  user: AdvancedUser
  contributionPeriodId: number
  contributions?: Array<Contribution>
  disabled?: boolean
}>(), {
  contributions: () => [],
  disabled: false,
})

const emit = defineEmits<{
  (e: "update:contribution", contribution: Contribution): void
  (e: "delete:contribution", id: number): void
}>()

const saving = ref(false)

const contribution = computed<Contribution | undefined>(() =>
  props.contributions.find(
    (c) => c.userId === props.user.id,
  ),
)

const hasContribution = computed(() => !!contribution.value)

const markPaid = async () => {
  if (!props.contributionPeriodId || props.disabled || saving.value) return
  saving.value = true
  try {
    const response = await createContribution({
      body: {
        userId: props.user.id as number,
        contributionPeriodId: props.contributionPeriodId as number,
      },
    })
    if (response.data) emit("update:contribution", response.data)
  } catch (e) {
    console.error("Failed to mark as paid:", e)
  } finally {
    saving.value = false
  }
}

const unmarkPaid = async () => {
  if (!contribution.value || props.disabled || saving.value) return
  saving.value = true
  try {
    await deleteContribution({path: {id: contribution.value.id as number}})
    emit("delete:contribution", contribution.value.id as number)
  } catch (e) {
    console.error("Failed to unmark as paid:", e)
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
span {
  font-weight: bold;
}
</style>
