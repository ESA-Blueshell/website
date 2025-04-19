import type BaseModel from "@/models/BaseModel";
import type {ResetType} from "@/models/enums/ResetType";


export default interface ActivationRequest extends BaseModel {
  token: string;
  resetType: ResetType;
  username?: string;
  password?: string;
}
