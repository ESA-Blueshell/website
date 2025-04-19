// Represents sorting direction
export enum SortDirection {
  ASC = 'ASC',
  DESC = 'DESC'
}

// Represents a single sort order
export interface SortOrder {
  property: string;
  direction: SortDirection;
}

// Pageable request interface (equivalent to Spring's Pageable)
export interface Pageable {
  page: number;         // Zero-based page index
  size: number;         // Number of items per page
  sort?: SortOrder[];   // Sorting criteria
}

// Page response interface (equivalent to Spring's Page<T>)
export interface Page<T> {
  content: T[];          // The content of the current page
  totalElements: number; // Total number of elements across all pages
  totalPages: number;    // Total number of pages
  number: number;        // Current page number (zero-based)
  size: number;          // Number of items per page
  first: boolean;        // Whether current page is first page
  last: boolean;         // Whether current page is last page
  empty: boolean;        // Whether current page is empty
}

// Default pageable configuration
export const defaultPageable: Pageable = {
  page: 0,
  size: 10,
  sort: []
};
