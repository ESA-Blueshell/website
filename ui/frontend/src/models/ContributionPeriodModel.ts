import type BaseModel from "@/models/BaseModel";

export default interface ContributionPeriodModel extends BaseModel {
  id?: number;
  startDate?: string;
  endDate?: string;
  halfYearFee?: number;
  fullYearFee?: number;
  alumniFee?: number;
  listId?: number;
}

export const defaultContributionPeriod: ContributionPeriodModel = {
  type: 'ContributionPeriodDTO',
  id: undefined,
  startDate: '',
  endDate: '',
  halfYearFee: undefined,
  fullYearFee: undefined,
  alumniFee: undefined,
  listId: undefined,
};
