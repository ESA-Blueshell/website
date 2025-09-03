/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { TelemetryDTO } from '../models/TelemetryDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class TelemetryControllerService {
    /**
     * @param platform
     * @param url
     * @returns TelemetryDTO OK
     * @throws ApiError
     */
    public static createTelemetry(
        platform: 'FACEBOOK' | 'LINKEDIN' | 'TWITTER' | 'INSTAGRAM',
        url: string,
    ): CancelablePromise<TelemetryDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/telemetry',
            query: {
                'platform': platform,
                'url': url,
            },
        });
    }
    /**
     * @param id
     * @returns TelemetryDTO OK
     * @throws ApiError
     */
    public static getTelemetry(
        id: string,
    ): CancelablePromise<TelemetryDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/telemetry/{id}',
            path: {
                'id': id,
            },
        });
    }
}
