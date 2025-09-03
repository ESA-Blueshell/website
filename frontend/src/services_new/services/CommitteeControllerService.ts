/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { AdvancedCommitteeDTO } from '../models/AdvancedCommitteeDTO';
import type { BlogDTO } from '../models/BlogDTO';
import type { EmailDTO } from '../models/EmailDTO';
import type { EventDTO } from '../models/EventDTO';
import type { SocialDTO } from '../models/SocialDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class CommitteeControllerService {
    /**
     * @param committeeId
     * @returns any OK
     * @throws ApiError
     */
    public static getCommitteeById(
        committeeId: number,
    ): CancelablePromise<(BlogDTO | EmailDTO | EventDTO | SocialDTO)> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/committees/{committeeId}',
            path: {
                'committeeId': committeeId,
            },
        });
    }
    /**
     * @param committeeId
     * @param requestBody
     * @returns any OK
     * @throws ApiError
     */
    public static updateCommittee(
        committeeId: number,
        requestBody: AdvancedCommitteeDTO,
    ): CancelablePromise<(BlogDTO | EmailDTO | EventDTO | SocialDTO)> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/committees/{committeeId}',
            path: {
                'committeeId': committeeId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param committeeId
     * @returns void
     * @throws ApiError
     */
    public static deleteCommitteeById(
        committeeId: number,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/committees/{committeeId}',
            path: {
                'committeeId': committeeId,
            },
        });
    }
    /**
     * @returns any OK
     * @throws ApiError
     */
    public static getCommittees(): CancelablePromise<Array<(BlogDTO | EmailDTO | EventDTO | SocialDTO)>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/committees',
        });
    }
    /**
     * @param requestBody
     * @returns AdvancedCommitteeDTO OK
     * @throws ApiError
     */
    public static createCommittee(
        requestBody: AdvancedCommitteeDTO,
    ): CancelablePromise<AdvancedCommitteeDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/committees',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @returns any OK
     * @throws ApiError
     */
    public static getCommitteesByUserId(): CancelablePromise<Array<(BlogDTO | EmailDTO | EventDTO | SocialDTO)>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/committeeMembers/committees',
        });
    }
}
