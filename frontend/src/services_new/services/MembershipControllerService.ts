/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { MembershipDTO } from '../models/MembershipDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class MembershipControllerService {
    /**
     * @param id
     * @returns MembershipDTO OK
     * @throws ApiError
     */
    public static getMembershipById(
        id: number,
    ): CancelablePromise<MembershipDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/memberships/{id}',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @param id
     * @param requestBody
     * @returns MembershipDTO OK
     * @throws ApiError
     */
    public static updateMembership1(
        id: number,
        requestBody: MembershipDTO,
    ): CancelablePromise<MembershipDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/memberships/{id}',
            path: {
                'id': id,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @returns MembershipDTO OK
     * @throws ApiError
     */
    public static getMemberships(): CancelablePromise<Array<MembershipDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/memberships',
        });
    }
    /**
     * @param requestBody
     * @returns MembershipDTO OK
     * @throws ApiError
     */
    public static createMembership(
        requestBody: MembershipDTO,
    ): CancelablePromise<MembershipDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/memberships',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
