import type BaseModel from "@/models/BaseModel";
import type CommitteeMemberModel from "@/models/CommitteeMemberModel.ts";

export default interface CommitteeModel extends BaseModel {
  id?: number;
  name?: string;
  description?: string;
  members?: CommitteeMemberModel[];
}

export const defaultCommittee: CommitteeModel = {
  type: 'CommitteeDTO',
  id: undefined,
  name: '',
  description: '',
  members: [],
};
