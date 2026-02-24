import {type Client, type Options as ClientOptions, type TDataShape} from "./client"
import {client} from "./client.gen"
import type {ApiError, PagedModelUserDetailResponse} from "./types.gen"

type RequestOptions<TData extends TDataShape = TDataShape, ThrowOnError extends boolean = boolean> =
  ClientOptions<TData, ThrowOnError> & {
    client?: Client
    meta?: Record<string, unknown>
  }

type FindDeletedUsersData = {
  body?: never
  path?: never
  query?: {
    page?: number
    size?: number
    sort?: string[]
  }
  url: "/users/deleted"
}

type FindDeletedUsersErrors = {
  400: ApiError
  401: ApiError
  403: ApiError
  404: ApiError
  500: ApiError
}

type FindDeletedUsersResponses = {
  200: PagedModelUserDetailResponse
}

export const findDeletedUsers = <ThrowOnError extends boolean = false>(
  options?: RequestOptions<FindDeletedUsersData, ThrowOnError>,
) => (options?.client ?? client).get<FindDeletedUsersResponses, FindDeletedUsersErrors, ThrowOnError>({
  responseType: "json",
  url: "/users/deleted",
  ...options,
})

type RestoreDeletedUserByIdData = {
  body?: never
  path: {
    userId: number
  }
  query?: never
  url: "/users/{userId}/restore"
}

type RestoreDeletedUserByIdErrors = {
  400: ApiError
  401: ApiError
  403: ApiError
  404: ApiError
  500: ApiError
}

type RestoreDeletedUserByIdResponses = {
  204: void
}

export const restoreDeletedUserById = <ThrowOnError extends boolean = false>(
  options: RequestOptions<RestoreDeletedUserByIdData, ThrowOnError>,
) => (options.client ?? client).put<RestoreDeletedUserByIdResponses, RestoreDeletedUserByIdErrors, ThrowOnError>({
  url: "/users/{userId}/restore",
  ...options,
})
