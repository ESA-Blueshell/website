/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ContributionPeriodDTO } from '../models/ContributionPeriodDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class ContributionPeriodControllerService {
    /**
     * @param id
     * @param requestBody
     * @returns ContributionPeriodDTO OK
     * @throws ApiError
     */
    public static updateContributionPeriod(
        id: number,
        requestBody: ContributionPeriodDTO,
    ): CancelablePromise<ContributionPeriodDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/contributionPeriods/{id}',
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
    public static deleteContributionPeriod(
        id: number,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/contributionPeriods/{id}',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @returns ContributionPeriodDTO OK
     * @throws ApiError
     */
    public static getContributionPeriods(): CancelablePromise<Array<ContributionPeriodDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/contributionPeriods',
        });
    }
    /**
     * @param requestBody
     * @returns ContributionPeriodDTO OK
     * @throws ApiError
     */
    public static createContributionPeriod(
        requestBody: ContributionPeriodDTO,
    ): CancelablePromise<ContributionPeriodDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/contributionPeriods',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
}
