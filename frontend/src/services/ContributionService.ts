import BaseService from "./BaseService.ts";
import type ContributionModel from "@/models/ContributionModel.ts";

export default class ContributionService extends BaseService {
  constructor() {
    super("/contributions");
  }

  async createContribution(contribution: ContributionModel): Promise<ContributionModel> {
    return this.post(contribution);
  }

  async markAsPaid(id: number, paid: boolean): Promise<ContributionModel> {
    return this.put({  }, `/${id}/paid`, { paid});
  }

  async getByPeriod(periodId: number): Promise<ContributionModel[]> {
    return this.get("", { contributionPeriodId: periodId });
  }

  async deleteContribution(id: number): Promise<void> {
    return this.delete(`/${id}`);
  }

  async sendReminder(periodId: number): Promise<void> {
    return this.post(null, `/contributionPeriods/${periodId}/remind`);
  }
}
