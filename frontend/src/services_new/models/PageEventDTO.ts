/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { EventDTO } from './EventDTO';
import type { PageableObject } from './PageableObject';
import type { SortObject } from './SortObject';
export type PageEventDTO = {
    totalElements?: number;
    totalPages?: number;
    size?: number;
    content?: Array<EventDTO>;
    number?: number;
    sort?: SortObject;
    pageable?: PageableObject;
    numberOfElements?: number;
    first?: boolean;
    last?: boolean;
    empty?: boolean;
};

