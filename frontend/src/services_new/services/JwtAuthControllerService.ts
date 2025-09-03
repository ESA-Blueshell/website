/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { JwtRequest } from '../models/JwtRequest';
import type { JwtResponse } from '../models/JwtResponse';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class JwtAuthControllerService {
    /**
     * @param requestBody
     * @returns JwtResponse OK
     * @throws ApiError
     */
    public static createAuthenticationToken(
        requestBody: JwtRequest,
    ): CancelablePromise<JwtResponse> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/auth',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
