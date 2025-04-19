import type BaseModel from "@/models/BaseModel";
import type SimpleUserModel from "@/models/user/SimpleUserModel.ts";

export default interface CommitteeMemberModel extends BaseModel {
  id?: number;
  role?: string;
  userId?: number;
  user?: SimpleUserModel;
  committeeId?: number;
}

export const defaultCommitteeMember: CommitteeMemberModel = {
  type: 'CommitteeMemberDTO',
  id: undefined,
  role: '',
  userId: undefined,
  user: undefined,
  committeeId: undefined,
};
