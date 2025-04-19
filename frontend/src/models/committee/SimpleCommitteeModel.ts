import type BaseModel from "@/models/BaseModel";

export default interface SimpleCommitteeModel extends BaseModel {
  id?: number;
  name?: string;
  description?: string;
}

export const defaultSimpleCommittee: SimpleCommitteeModel = {
  type: 'SimpleCommitteeDTO',
  id: undefined,
  name: '',
  description: '',
};
