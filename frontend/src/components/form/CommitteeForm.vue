<template>
  <div>
    <Form
      ref="formRef"
      as="div"
    >
      <v-container>
        <v-row class="mt-1">
          <v-col cols="12">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="committee.name"
              name="name"
              rules="required|minChars:3|maxChars:100"
            >
              <v-text-field
                :disabled="isReadonly"
                :error-messages="errors"
                :model-value="value"
                label="Committee name"
                required
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>
        </v-row>

        <v-row>
          <v-col>
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="committee.description"
              name="description"
              rules="required|minChars:10|maxChars:10000"
            >
              <markdown-field
                :disabled="isReadonly"
                :error-messages="errors"
                :model-value="value"
                label="Description"
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>
        </v-row>

        <v-container>
          <v-row
            v-for="(member, i) in committee.members ?? []"
            :key="member.id ?? i"
            v-model="committee.members[i]"
            class="mt-4"
            dense
          >
            <v-col cols="4">
              <Field
                v-slot="{ value, errors, handleChange, handleBlur }"
                v-model="committee.members[i].role"
                :name="`members[${i}].role`"
                rules="maxChars:120"
              >
                <v-text-field
                  :disabled="isReadonly"
                  :error-messages="errors"
                  :model-value="value"
                  hide-details="auto"
                  label="Role"
                  @blur="handleBlur"
                  @update:model-value="handleChange"
                />
              </Field>
            </v-col>

            <v-col cols="7">
              <Field
                v-slot="{ value, errors, handleChange, handleBlur }"
                v-model="committee.members[i].userId"
                :name="`members[${i}].userId`"
                :rules="`required|committeeUserIsMember|uniqueCommitteeMember:${i}`"
              >
                <user-select
                  :disabled="isReadonly"
                  :error-messages="errors"
                  :model-value="value"
                  :users="props.users"
                  label="Member name"
                  @blur="handleBlur"
                  @update:model-value="handleChange"
                />
              </Field>
            </v-col>

            <v-col cols="1">
              <v-btn
                :disabled="isReadonly"
                icon="mdi-close"
                variant="plain"
                @click="removeMember(committee.members[i].userId)"
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
        class="mb-5"
        justify="end"
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
import {defineRule, Field, Form, type FormContext} from "vee-validate"
import {apply} from "@/plugins/validation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import MarkdownField from "@/components/form/fields/MarkdownField.vue"
import UserSelect from "@/components/form/fields/UserSelect.vue"
import {
  type AdvancedCommittee,
  type AdvancedUser,
  type CommitteeMember,
  createCommittee,
  Role,
  updateCommittee,
} from "@/services/api"
import {useStore} from "vuex"

// Props align with AdvancedUser form style (submission controls) + required users list
const props = withDefaults(
  defineProps<{
    users: AdvancedUser[]
    showSubmit?: boolean
    submitText?: string
  }>(),
  {
    showSubmit: false,
    submitText: "Submit",
  },
)

// Emits aligned to AdvancedUser form
const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

// Use defineModel like AdvancedUser form
const committee = defineModel<AdvancedCommittee>({
  default: () => ({name: "", description: "", members: []} as AdvancedCommittee),
})

// Store-derived readonly behavior to mirror pattern in AdvancedUser form
const store = useStore()
const isLoggedIn = computed(() => store.getters.isLoggedIn)
const isBoard = computed(() => store.getters.isBoard)
const isReadonly = computed(() => isLoggedIn.value && !isBoard.value)

// Validation rules
defineRule("committeeUserIsMember", (userId: number | string) => {
  if (!userId && userId !== 0) return "Select a user"
  const u = props.users.find(u => Number(u.id) === Number(userId))
  if (!u) return "Select a user"
  return u.roles?.includes(Role.MEMBER) || "Committee members must be members of the association"
})

defineRule("uniqueCommitteeMember", (userId: number, [idx]: string[]) => {
  if (!userId && userId !== 0) return true
  const i = Number(idx)
  committee.value.members ??= []
  const dup = committee.value.members.some((m: CommitteeMember, pos: number) => pos !== i && Number(m?.userId) === Number(userId))
  return !dup || "Member already in this committee"
})

const formRef = ref<FormContext>()
const isSaving = ref<boolean>(false)
const isCreating = computed<boolean>(() => !committee.value?.id)

function addMember() {
  committee.value.members ??= []
  committee.value.members.push({role: "", userId: 1, committeeId: committee.value.id})
}

function removeMember(id: number) {
  committee.value.members ??= []
  committee.value.members = committee.value.members.filter((m: CommitteeMember) => id !== m.userId)
}

const validate = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const save = async (): Promise<AdvancedCommittee | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    return null
  }

  isSaving.value = true
  try {
    const hasId = Boolean(committee.value?.id)
    const resp = hasId
      ? await updateCommittee({
        path: {id: committee.value!.id!},
        body: committee.value!,
        throwOnError: true,
      })
      : await createCommittee({
        body: committee.value!,
        throwOnError: true,
      })

    committee.value = resp.data!
    emit("submitted", true)
    return resp.data!
  } catch (error: unknown) {
    if (!formRef.value || !apply(formRef.value, error)) {
      $handleNetworkError(error)
    }
    emit("submitted", false)
    return null
  } finally {
    isSaving.value = false
  }
}

defineExpose({validate, save})
</script>

<style lang="scss" scoped>
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}

.v-col {
  padding-bottom: 0;
  padding-top: 0;
}

.text-error {
  color: rgb(var(--v-theme-error));
}
</style>
