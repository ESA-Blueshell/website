import type BaseModel from "@/models/BaseModel";

export default interface SimpleUserModel extends BaseModel {
  id: number;
  username?: string;
  discord?: string;
  firstName?: string;
  prefix?: string;
  lastName?: string;
  fullName?: string;
  email?: string;
}

export const defaultSimpleUser: SimpleUserModel = {
  type: 'SimpleUserDTO',
  id: 0,
  username: '',
  discord: '',
  firstName: '',
  prefix: '',
  lastName: '',
  fullName: '',
  email: '',
};
