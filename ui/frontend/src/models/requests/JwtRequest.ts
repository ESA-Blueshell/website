import type BaseModel from "@/models/BaseModel";


export default interface JwtRequest extends BaseModel {
  username?: string;
  password?: string;
}
