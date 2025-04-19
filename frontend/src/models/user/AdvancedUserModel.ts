import type SimpleUserModel from "@/models/user/SimpleUserModel.ts";
import type {Role} from "@/models/enums/Role";
import type MembershipModel from "@/models/MembershipModel.ts";
import {MemberType} from "@/models/enums/MemberType"

export default interface AdvancedUserModel extends SimpleUserModel {
  initials?: string;
  roles?: Role[];
  dateOfBirth?: string;
  phoneNumber?: string;
  postalCode?: string;
  address?: string;
  city?: string;
  country?: string;
  nationality?: string;
  signature?: File;
  membership?: MembershipModel;
  newsletter?: boolean;
  photoConsent?: boolean;
  ehbo?: boolean;
  bhv?: boolean;
  enabled?: boolean;
  incasso?: boolean;
  memberType?: MemberType;
  createdAt?: string;
  gender?: string;
  study?: string;
  studentNumber?: string;
  password?: string;
}

export const defaultAdvancedUser: AdvancedUserModel = {
  type: 'AdvancedUserDTO',
  id: 0,
  username: '',
  discord: '',
  firstName: '',
  prefix: '',
  lastName: '',
  fullName: '',
  email: '',
  initials: '',
  roles: [],
  dateOfBirth: '',
  phoneNumber: '',
  postalCode: '',
  address: '',
  city: '',
  country: '',
  nationality: '',
  signature: undefined,
  membership: undefined,
  newsletter: false,
  photoConsent: false,
  ehbo: false,
  bhv: false,
  enabled: false,
  incasso: false,
  memberType: undefined,
  createdAt: '',
  gender: '',
  study: '',
  studentNumber: '',
  password: '',
};
