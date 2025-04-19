import type BaseModel from "../models/BaseModel";

export default interface ActivateUserRequest extends BaseModel {
  username: string;
  token: string;
}

