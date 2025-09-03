/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { BlogDTO } from '../models/BlogDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class BlogControllerService {
    /**
     * @returns BlogDTO OK
     * @throws ApiError
     */
    public static findAll(): CancelablePromise<Array<BlogDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/blogs',
        });
    }
    /**
     * @param requestBody
     * @returns BlogDTO OK
     * @throws ApiError
     */
    public static create2(
        requestBody: BlogDTO,
    ): CancelablePromise<BlogDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/blogs',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param id
     * @returns BlogDTO OK
     * @throws ApiError
     */
    public static findById(
        id: string,
    ): CancelablePromise<BlogDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/blogs/{id}',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @param id
     * @returns void
     * @throws ApiError
     */
    public static deleteById(
        id: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/blogs/{id}',
            path: {
                'id': id,
            },
        });
    }
}
