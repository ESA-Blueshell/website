import type BlogModel from "@/models/BlogModel.ts";
import BaseService from "@/services/BaseService.ts";

export default class BlogService extends BaseService {
  constructor() {
    super("/blogs");
  }

  async getBlog(id: string): Promise<BlogModel> {
    return this.get(`/${id}`);
  }

  async getBlogs(): Promise<BlogModel[]> {
    return this.get("");
  }
}
