/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { EventDTO } from '../models/EventDTO';
import type { Pageable } from '../models/Pageable';
import type { PageEventDTO } from '../models/PageEventDTO';
import type { CancelablePromise } from '../core/CancelablePromise';
import { OpenAPI } from '../core/OpenAPI';
import { request as __request } from '../core/request';
export class EventControllerService {
    /**
     * @param eventId
     * @param requestBody
     * @returns EventDTO OK
     * @throws ApiError
     */
    public static updateEvent(
        eventId: number,
        requestBody: EventDTO,
    ): CancelablePromise<EventDTO> {
        return __request(OpenAPI, {
            method: 'PUT',
            url: '/events/{eventId}',
            path: {
                'eventId': eventId,
            },
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param eventId
     * @returns void
     * @throws ApiError
     */
    public static deleteEventById(
        eventId: number,
    ): CancelablePromise<void> {
        return __request(OpenAPI, {
            method: 'DELETE',
            url: '/events/{eventId}',
            path: {
                'eventId': eventId,
            },
        });
    }
    /**
     * @param from
     * @param to
     * @returns EventDTO OK
     * @throws ApiError
     */
    public static getEvents(
        from?: string,
        to?: string,
    ): CancelablePromise<Array<EventDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/events',
            query: {
                'from': from,
                'to': to,
            },
        });
    }
    /**
     * @param requestBody
     * @returns EventDTO OK
     * @throws ApiError
     */
    public static createEvent(
        requestBody: EventDTO,
    ): CancelablePromise<EventDTO> {
        return __request(OpenAPI, {
            method: 'POST',
            url: '/events',
            body: requestBody,
            mediaType: 'application/json',
        });
    }
    /**
     * @param editable
     * @returns EventDTO OK
     * @throws ApiError
     */
    public static getUpcomingEvents(
        editable: boolean = false,
    ): CancelablePromise<Array<EventDTO>> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/upcoming',
            query: {
                'editable': editable,
            },
        });
    }
    /**
     * @param id
     * @returns EventDTO OK
     * @throws ApiError
     */
    public static getEventById(
        id: number,
    ): CancelablePromise<EventDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/events/{id}',
            path: {
                'id': id,
            },
        });
    }
    /**
     * @param editable
     * @returns any OK
     * @throws ApiError
     */
    public static getPastEvents(
        editable: boolean = false,
    ): CancelablePromise<{
        parallel?: boolean;
    }> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/events/past',
            query: {
                'editable': editable,
            },
        });
    }
    /**
     * @param pageable
     * @returns PageEventDTO OK
     * @throws ApiError
     */
    public static getEventsPageable(
        pageable: Pageable,
    ): CancelablePromise<PageEventDTO> {
        return __request(OpenAPI, {
            method: 'GET',
            url: '/events/pageable',
            query: {
                'pageable': pageable,
            },
        });
    }
}
