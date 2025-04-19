import type BaseModel from "@/models/BaseModel";
import type {MemberType} from "@/models/enums/MemberType.ts";

export default interface MembershipModel extends BaseModel {
  id?: number;
  date?: string;
  city?: string;
  country?: string;
  signature?: File;
  memberType?: MemberType;
  startDate?: string;
  endDate?: string;
}

export const defaultMembership: MembershipModel = {
  type: 'MembershipDTO',
  id: undefined,
  date: '',
  city: '',
  country: '',
  signature: undefined,
  memberType: undefined,
  startDate: '',
  endDate: '',
};
