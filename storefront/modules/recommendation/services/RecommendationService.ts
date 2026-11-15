import { get } from '../../../common/services/ApiClientService';
import { SimilarProduct } from '../models/SimilarProduct';

/** Similar products by text embeddings (YAS path); `signal` bounds the wait (the product page must not hang on it). */
export const getSimilarProducts = (productId: number, signal?: AbortSignal) =>
  get<SimilarProduct[]>(`/recommendation/embedding/product/${productId}/similarity`, signal);
