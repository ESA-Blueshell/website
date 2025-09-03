/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { RedirectDTO } from '../models/RedirectDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class RedirectControllerService {
    /**
     * @param id
     * @returns string OK
     * @throws ApiError
     */
    public static addRedirect(
        id: string,
    ): CancelablePromise<string> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/telemetry/redirect',
            query: {
                'id': id,
            },
        });
    }
    /**
     * @param id
     * @returns void
     * @throws ApiError
     */
    public static deleteRedirect(
        id: string,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/telemetry/redirect',
            query: {
                'id': id,
            },
        });
    }
    /**
     * @param from
     * @param to
     * @returns RedirectDTO OK
     * @throws ApiError
     */
    public static getRedirects(
        from?: string,
        to?: string,
    ): CancelablePromise<Array<RedirectDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/telemetry/redirects',
            query: {
                'from': from,
                'to': to,
            },
        });
    }
}
