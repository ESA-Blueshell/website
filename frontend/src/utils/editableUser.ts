import type {CreateUserRequest, UserDetailResponse} from "@/services/api"

export type EditableUser = CreateUserRequest & Partial<UserDetailResponse>

export const toEditableUser = (
  data: UserDetailResponse,
  current?: EditableUser,
): EditableUser => ({
  ...current,
  ...data,
  discord: data.discord ?? current?.discord ?? "",
  phoneNumber: data.phoneNumber ?? current?.phoneNumber ?? "",
})
