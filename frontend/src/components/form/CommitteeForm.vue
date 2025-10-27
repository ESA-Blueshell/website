<template>
  <div>
    <Form
      ref="formRef"
      as="div"
    >
      <v-container>
        <v-row class="mt-1 tight-row">
          <v-col cols="12">
            <VvField
              v-model="committee.name"
              name="name"
              label="Committee name"
              rules="required|minChars:3|maxChars:100"
              :disabled="isReadonly"
            />
          </v-col>
        </v-row>

        <v-row class="tight-row">
          <v-col>
            <VvField
              v-model="committee.description"
              :component="MarkdownField"
              name="description"
              label="Description"
              rules="required|minChars:10|maxChars:10000"
              :disabled="isReadonly"
            />
          </v-col>
        </v-row>

        <v-container :key="forceUpdateKey">
          <v-row
            v-for="(member, i) in committee.members"
            :key="member.userId || `new-${i}`"
            class="tight-row my-3"
          >
            <v-col cols="4">
              <VvField
                v-model="committee.members[i].role"
                :disabled="isReadonly"
                :name="`members[${i}].role`"
                label="Role"
                rules="maxChars:120"
                :component-props="{ 'hide-details': 'auto' }"
              />
            </v-col>

            <v-col cols="7">
              <VvField
                v-model="committee.members[i].userId"
                :component="UserSelect"
                :component-props="{ users: props.users, label: 'Member name' }"
                :disabled="isReadonly"
                :name="`members[${i}].userId`"
                :rules="`required|committeeUserIsMember|uniqueCommitteeMember:${i}`"
              />
            </v-col>

            <v-col cols="1">
              <v-btn
                :disabled="isReadonly"
                icon="mdi-close"
                variant="plain"
                @click="removeMember(committee.members[i].userId as number)"
              />
            </v-col>
          </v-row>

          <v-btn
            v-if="!isReadonly"
            block
            class="mt-4"
            variant="outlined"
            @click.prevent="addMember()"
          >
            Add member
          </v-btn>
        </v-container>
      </v-container>

      <v-row
        align="end"
        justify="end"
        class="mb-5 tight-row"
      >
        <v-col
          v-if="props.showSubmit"
          cols="auto"
        >
          <v-btn
            :disabled="isSaving"
            :loading="isSaving"
            :prepend-icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            size="large"
            type="button"
            @click="save"
          >
            {{ props.submitText }}
          </v-btn>
        </v-col>
      </v-row>
    </Form>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {defineRule, Form} from "vee-validate"
import MarkdownField from "@/components/form/fields/MarkdownField.vue"
import UserSelect from "@/components/form/fields/UserSelect.vue"
import VvField from "@/components/form/fields/VvField.vue"
import {
  type AdvancedCommittee,
  type AdvancedUser,
  type CommitteeMember,
  createCommittee,
  Role,
  updateCommittee,
} from "@/services/api"
import {handleSubmitError, useReadonly, useSaving, useVeeForm} from "@/composables/formUtils"

const props = withDefaults(defineProps<{
  users: AdvancedUser[];
  showSubmit?: boolean;
  submitText?: string
}>(), {
  showSubmit: false,
  submitText: "Submit",
})

const forceUpdateKey = ref(0)

const getDefaultMember: () => CommitteeMember = () => ({
  role: "",
  userId: null as unknown as number,
  committeeId: committee.value.id,
})

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

const committee = defineModel<AdvancedCommittee>({
  default: () => ({
    name: "",
    description: "",
    members: [{role: "", userId: null as unknown as number}],
  } as AdvancedCommittee),
})

const {isReadonly} = useReadonly()

defineRule("committeeUserIsMember", (userId: number | string) => {
  if (!userId && userId !== 0) return "Select a user"
  const u = props.users.find((u) => Number(u.id) === Number(userId))
  if (!u) return "Select a user"
  return u.roles?.includes(Role.MEMBER) || "Committee members must be members of the association"
})

defineRule("uniqueCommitteeMember", (userId: number, [idx]: string[]) => {
  if (!userId && userId !== 0) return true
  const i = Number(idx)
  const dup = committee.value.members.some((m: CommitteeMember, pos: number) => pos !== i && Number(m?.userId) === Number(userId))
  return !dup || "Member already in this committee"
})

const {formRef, validate} = useVeeForm()
const {isSaving, withSaving} = useSaving()
const isCreating = computed<boolean>(() => !committee.value?.id)

function addMember() {
  committee.value.members = [...committee.value.members, getDefaultMember()]
  forceUpdateKey.value++
}

function removeMember(id: number) {
  committee.value.members = committee.value.members.filter((m: CommitteeMember) => id !== m.userId)
  forceUpdateKey.value++
}

const save = async (): Promise<AdvancedCommittee | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    return null
  }
  try {
    const resp = await withSaving(async () => {
      const hasId = Boolean(committee.value?.id)
      return hasId
        ? await updateCommittee({path: {id: committee.value!.id!}, body: committee.value!, throwOnError: true})
        : await createCommittee({body: committee.value!, throwOnError: true})
    })
    committee.value = resp.data!
    emit("submitted", true)
    return resp.data!
  } catch (error: unknown) {
    handleSubmitError(formRef.value, error)
    emit("submitted", false)
    return null
  }
}

defineExpose({validate, save})
</script>

<style lang="scss" scoped>
.text-error {
  color: rgb(var(--v-theme-error));
}
</style>
