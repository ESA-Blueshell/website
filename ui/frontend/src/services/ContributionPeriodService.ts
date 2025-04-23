import BaseApiService from "./BaseApiService.ts";
import type ContributionPeriodModel from "@/models/ContributionPeriodModel.ts";
import type ContributionModel from "@/models/ContributionModel.ts";

export default class ContributionPeriodService extends BaseApiService {
  constructor() {
    super("/contributionPeriods");
  }

  async getContributionPeriods(): Promise<ContributionPeriodModel[]> {
    return this.get();
  }

  async createPeriod(period: ContributionPeriodModel): Promise<ContributionPeriodModel> {
    return this.post(period);
  }

  async updatePeriod(id: number, period: ContributionPeriodModel): Promise<ContributionPeriodModel> {
    return this.put(period, `/${id}`);
  }

  async deletePeriod(id: number): Promise<void> {
    return this.delete(`/${id}`);
  }

  async getPeriodContributions(id: number): Promise<ContributionModel[]> {
    return this.get(`/${id}/contributions`);
  }
}
