import type BaseModel from "@/models/BaseModel";

export default interface PasswordResetRequest extends BaseModel {
  username: string;
  token: string;
  password: string;
}
