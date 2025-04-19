import type BaseModel from "@/models/BaseModel";

export default interface BlogModel extends BaseModel {
  id?: string;
  title: string;
  html: string;
  publishedAt: string;
}

export const defaultBlog: BlogModel = {
  type: 'BlogDTO',
  id: '',
  title: '',
  html: '',
  publishedAt: '',
};
