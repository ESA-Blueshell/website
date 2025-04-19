import type BaseModel from "@/models/BaseModel";
import type CommitteeMemberModel from "@/models/CommitteeMemberModel.ts";

export default interface AdvancedCommitteeModel extends BaseModel {
  id?: number;
  name?: string;
  description?: string;
  members?: CommitteeMemberModel[];
}

export const defaultAdvancedCommittee: AdvancedCommitteeModel = {
  type: 'AdvancedCommitteeDTO',
  id: undefined,
  name: '',
  description: '',
  members: [],
};
