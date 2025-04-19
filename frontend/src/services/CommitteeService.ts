// ./src/services/CommitteeService.ts
import BaseService from "./BaseService.ts";
import type {CommitteeMemberModel, CommitteeModel} from "@/models";

export default class CommitteeService extends BaseService {
  constructor() {
    super("/committees");
  }

  async getCommittees(isMember?: boolean): Promise<CommitteeModel[]> {
    return this.get("", { isMember });
  }

  async createCommittee(committee: CommitteeModel): Promise<CommitteeModel> {
    return this.post(committee);
  }

  async updateCommittee(id: number, committee: CommitteeModel): Promise<CommitteeModel> {
    return this.put(committee, `/${id}`);
  }

  async deleteCommittee(id: number): Promise<void> {
    return this.delete(`/${id}`);
  }

  async getMembers(committeeId: number): Promise<CommitteeMemberModel[]> {
    return this.get(`/${committeeId}/members`);
  }
}
