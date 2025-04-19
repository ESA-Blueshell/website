import type BaseModel from "@/models/BaseModel";

export default interface NewsModel extends BaseModel {
  id?: string;
  creatorId: string;
  creatorUsername?: string;
  lastEditorId: string;
  lastEditorUsername?: string;
  newsType: string;
  title: string;
  content: string;
  postedAt?: string;
}

export const defaultNews: NewsModel = {
  type: 'NewsDTO',
  id: '',
  creatorId: '',
  creatorUsername: '',
  lastEditorId: '',
  lastEditorUsername: '',
  newsType: '',
  title: '',
  content: '',
  postedAt: '',
};
