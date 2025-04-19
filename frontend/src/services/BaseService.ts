// ./src/services/BaseService.ts
import { AxiosError, type AxiosInstance } from 'axios';
import {Store} from 'vuex';
import store, {type State} from "../plugins/store";
import api from "@/plugins/api";
import { $handleNetworkError } from "@/plugins/handleNetworkError";

export default class BaseService {
  protected axios: AxiosInstance = api;
  protected store: Store<State> = store;
  protected baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  protected authHeader() {
    const login = this.store.getters.getLogin;
    console.log(login);
    return login ? { headers: { Authorization: `Bearer ${login.token}` } } : {};
  }

  protected async get<T>(path = "", params = {}): Promise<T> {
    try {
      const response = await this.axios.get(`${this.baseUrl}${path}`, {
        ...this.authHeader(),
        params
      });
      return response.data;
    } catch (e) {
      this.handleError(e);
      throw e;
    }
  }

  protected async post<T>(data: unknown, path = "", params = {}): Promise<T> {
    try {
      const response = await this.axios.post(`${this.baseUrl}${path}`, data, {
        ...this.authHeader(),
        params
      });
      return response.data;
    } catch (e) {
      this.handleError(e);
      throw e;
    }
  }

  protected async put<T>(data: unknown, path = "", params = {}): Promise<T> {
    try {
      const response = await this.axios.put(`${this.baseUrl}${path}`, data, {
        ...this.authHeader(),
        params
      });
      return response.data;
    } catch (e) {
      this.handleError(e);
      throw e;
    }
  }

  protected async delete(path = "", params = {}): Promise<void> {
    try {
      await this.axios.delete(`${this.baseUrl}${path}`, {
        ...this.authHeader(),
        params
      });
    } catch (e) {
      this.handleError(e);
      throw e;
    }
  }

  private handleError(e: unknown) {
    if (e instanceof AxiosError) {
      const message = e.response?.data?.message || e.message;
      this.store.commit('setStatusSnackbarMessage', message);
    } else {
      $handleNetworkError(e);
    }
  }
}
