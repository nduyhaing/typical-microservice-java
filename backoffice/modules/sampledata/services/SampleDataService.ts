import { post } from '../../../common/services/ApiClientService';

/** What `POST /sampledata/backoffice/sampledata` answers (decisions.md 5-50). */
export type SampleDataResult = {
  message: string;
  /** rows inserted per catalog table; 0 where the sample rows were already there */
  inserted: Record<string, number>;
  /** sample products that had been deleted (unpublished) and are published again */
  republished: number;
  total: number;
};

/** Inserts the missing sample catalog rows (admin only; idempotent). YAS: public `POST /storefront/sampledata`. */
export const loadSampleData = () => post<SampleDataResult>('/sampledata/backoffice/sampledata', undefined);
