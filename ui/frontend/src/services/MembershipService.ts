import BaseApiService from "./BaseApiService.ts";
import type {MembershipModel} from "@/models";

export default class MembershipServiceService extends BaseApiService {
  constructor() {
    super("/memberships");
  }

  async getMemberships(): Promise<MembershipModel[]> {
    return this.get();
  }

  async getMembership(id: number): Promise<MembershipModel> {
    return this.get(`/${id}`);
  }

  async createMembership(membership: MembershipModel): Promise<MembershipModel> {
    return this.post(membership);
  }

  async updateMembership(id: number, membership: MembershipModel): Promise<MembershipModel> {
    return this.put(membership, `/${id}`);
  }
}
