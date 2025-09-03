/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { SponsorDTO } from '../models/SponsorDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class SponsorControllerService {
    /**
     * @param id
     * @returns SponsorDTO OK
     * @throws ApiError
     */
    public static getSponsorById(
        id: number,
    ): CancelablePromise<SponsorDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/sponsors/{id}',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @param id
     * @param requestBody
     * @returns any OK
     * @throws ApiError
     */
    public static createOrUpdateSponsor(
        id: number,
        requestBody: SponsorDTO,
    ): CancelablePromise<Record<string, any>> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/sponsors/{id}',
            path: {
                'id': id,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param id
     * @returns void
     * @throws ApiError
     */
    public static deleteSponsorById(
        id: number,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/sponsors/{id}',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @returns SponsorDTO OK
     * @throws ApiError
     */
    public static getSponsors(): CancelablePromise<Array<SponsorDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/sponsors',
        });
    }
    /**
     * @param requestBody
     * @returns SponsorDTO OK
     * @throws ApiError
     */
    public static createSponsor(
        requestBody: SponsorDTO,
    ): CancelablePromise<SponsorDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/sponsors',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
