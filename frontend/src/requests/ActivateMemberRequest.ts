import type BaseModel from "../models/BaseModel";

export default interface ActivateMemberRequest extends BaseModel {
  username: string;
  token: string;
  password: string;
}

