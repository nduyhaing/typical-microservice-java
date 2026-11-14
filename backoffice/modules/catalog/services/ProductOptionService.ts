import { get, post } from '../../../common/services/ApiClientService';
import { ProductOption } from '../models/Product';

const base = '/product/backoffice/product-options';

export const getProductOptions = () => get<ProductOption[]>(base);

export const createProductOption = (name: string) => post<ProductOption>(base, { name });
