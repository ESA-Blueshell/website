import type BaseModel from "@/models/BaseModel";

export default interface ContributionModel extends BaseModel {
  id?: number;
  userId?: number;
  contributionPeriodId?: number;
  paid?: boolean;
  remindedAt?: string;
}

export const defaultContribution: ContributionModel = {
  type: 'ContributionDTO',
  id: undefined,
  userId: undefined,
  contributionPeriodId: undefined,
  paid: false,
  remindedAt: '',
};
