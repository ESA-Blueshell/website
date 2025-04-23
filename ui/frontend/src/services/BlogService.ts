import type BlogModel from "@/models/BlogModel.ts";
import BaseApiGatewayService from "@/services/BaseApiGatewayService.ts";

export default class BlogService extends BaseApiGatewayService {
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
