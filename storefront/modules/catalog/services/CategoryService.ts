import { get } from '../../../common/services/ApiClientService';
import { Brand } from '../models/Brand';
import { Category } from '../models/Category';

export const getCategories = () => get<Category[]>('/product/storefront/categories');

export const getBrands = () => get<Brand[]>('/product/storefront/brands');
