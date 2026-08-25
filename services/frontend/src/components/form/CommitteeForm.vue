<script lang="ts" setup>
import {computed, ref} from "vue"
import {defineRule, Form} from "vee-validate"
import MarkdownField from "@/components/form/fields/MarkdownField.vue"
import UserSelect from "@/components/form/fields/UserSelect.vue"
import VvField from "@/components/form/fields/VvField.vue"
import SubmitButton from "@/components/form/SubmitButton.vue"
import {
  type CommitteeMemberRequest,
  type CreateCommitteeRequest,
  createCommittee,
  Role,
  type UpdateCommitteeRequest,
  updateCommittee,
  type UserDetailResponse,
} from "@/services/api"
import {handleSubmitError, useReadonly, useSaving, useSubmitFeedback, useVeeForm} from "@/composables/formUtils"

type CommitteeModel = {
  id?: number
  name: string
  description: string
  members: CommitteeMemberRequest[]
  version?: number
}

const props = withDefaults(
  defineProps<{
    users: UserDetailResponse[]
    showSubmit?: boolean
    submitText?: string
  }>(),
  {
    showSubmit: false,
    submitText: "Submit",
  },
)

const forceUpdateKey = ref(0)

const getDefaultMember: () => CommitteeMemberRequest = () => ({
  role: "",
  userId: 0,
})

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

const committee = defineModel<CommitteeModel>({
  default: () =>
    ({
      name: "",
      description: "",
      members: [
        {
          role: "",
          userId: 0,
        },
      ],
    } as CommitteeModel),
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
  const dup = committee.value.members.some(
    (m: CommitteeMemberRequest, pos: number) => pos !== i && Number(m?.userId) === Number(userId),
  )
  return !dup || "Member already in this committee"
})

const {formRef, validate} = useVeeForm()
const {isSaving, withSaving} = useSaving()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const isCreating = computed<boolean>(() => !committee.value?.id)

function addMember() {
  committee.value.members = [...committee.value.members, getDefaultMember()]
  forceUpdateKey.value++
}

function removeMember(id: number) {
  committee.value.members = committee.value.members.filter((m: CommitteeMemberRequest) => id !== m.userId)
  forceUpdateKey.value++
}

const toCreateCommitteeRequest = (value: CommitteeModel): CreateCommitteeRequest => ({
  name: value.name,
  description: value.description,
  members: value.members.map((member) => ({
    role: member.role,
    userId: Number(member.userId),
  })),
})

const toUpdateCommitteeRequest = (value: CommitteeModel): UpdateCommitteeRequest => ({
  ...toCreateCommitteeRequest(value),
  version: value.version ?? 0,
})

const save = async (): Promise<CommitteeModel | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
  try {
    const resp = await withSaving(async () => {
      const hasId = Boolean(committee.value?.id)
      return hasId
        ? await updateCommittee({
          path: {id: committee.value!.id!},
          body: toUpdateCommitteeRequest(committee.value!),
          throwOnError: true,
        })
        : await createCommittee({body: toCreateCommitteeRequest(committee.value!), throwOnError: true})
    })
    committee.value = resp.data!
    emit("submitted", true)
    setSubmitResult(true)
    return resp.data!
  } catch (error: unknown) {
    handleSubmitError(formRef.value, error)
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
}

defineExpose({validate, save})
</script>

<template>
  <div>
    <Form
      ref="formRef"
      as="div"
      data-testid="committee-form"
    >
      <v-container>
        <v-row class="mt-1">
          <v-col cols="12">
            <VvField
              v-model="committee.name"
              :disabled="isReadonly"
              label="Committee name"
              name="name"
              rules="required|minChars:3|maxChars:100"
            />
          </v-col>
        </v-row>

        <v-row>
          <v-col>
            <VvField
              v-model="committee.description"
              :component="MarkdownField"
              :disabled="isReadonly"
              label="Description"
              name="description"
              rules="required|minChars:10|maxChars:10000"
            />
          </v-col>
        </v-row>

        <v-container :key="forceUpdateKey">
          <v-row
            v-for="(member, i) in committee.members"
            :key="member.userId || `new-${i}`"
            class="my-3"
          >
            <v-col cols="4">
              <VvField
                v-model="committee.members[i].role"
                :component-props="{ 'hide-details': 'auto' }"
                :disabled="isReadonly"
                :name="`members[${i}].role`"
                label="Role"
                rules="maxChars:50"
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
                :data-testid="`committee-form-remove-member-btn-${i}`"
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
            data-testid="committee-form-add-member-btn"
            variant="outlined"
            @click.prevent="addMember()"
          >
            Add member
          </v-btn>
        </v-container>
      </v-container>

      <v-row
        align="end"
        class="mb-5"
        justify="end"
      >
        <v-col
          v-if="props.showSubmit"
          cols="auto"
        >
          <submit-button
            :disabled="isSaving"
            :icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            :data-submit-mode="isCreating ? 'create' : 'update'"
            :loading="isSaving"
            :show-submit-status="showSubmitStatus"
            :submit-state="submitState"
            :text="props.submitText"
            data-testid="committee-form-submit-btn"
            @click="save"
          />
        </v-col>
      </v-row>
    </Form>
  </div>
</template>

<style lang="scss" scoped>
.text-error {
  color: rgb(var(--v-theme-error));
}
</style>
