/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ContributionDTO } from '../models/ContributionDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ContributionControllerService {
    /**
     * @param id
     * @param paid
     * @returns ContributionDTO OK
     * @throws ApiError
     */
    public static paid(
        id: number,
        paid: boolean,
    ): CancelablePromise<ContributionDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/contributions/{id}/paid',
            path: {
                'id': id,
            },
            query: {
                'paid': paid,
            },
        });
    }
    /**
     * @param periodId
     * @returns any OK
     * @throws ApiError
     */
    public static sendContributionReminder(
        periodId: number,
    ): CancelablePromise<any> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/contributionPeriods/{periodId}/contributions/remind',
            path: {
                'periodId': periodId,
            },
        });
    }
    /**
     * @param contributionPeriodId
     * @returns any OK
     * @throws ApiError
     */
    public static getAll1(
        contributionPeriodId?: number,
    ): CancelablePromise<{
        parallel?: boolean;
    }> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/contributions',
            query: {
                'contributionPeriodId': contributionPeriodId,
            },
        });
    }
    /**
     * @param requestBody
     * @returns ContributionDTO OK
     * @throws ApiError
     */
    public static create1(
        requestBody: ContributionDTO,
    ): CancelablePromise<ContributionDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/contributions',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param periodId
     * @returns ContributionDTO OK
     * @throws ApiError
     */
    public static getContributionsByPeriodId(
        periodId: number,
    ): CancelablePromise<Array<ContributionDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/contributionPeriods/{periodId}/contributions',
            path: {
                'periodId': periodId,
            },
        });
    }
    /**
     * @param id
     * @returns void
     * @throws ApiError
     */
    public static delete1(
        id: number,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/contributions/{id}',
            path: {
                'id': id,
            },
        });
    }
}
