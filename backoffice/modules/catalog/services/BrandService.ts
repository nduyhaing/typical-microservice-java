import { del, get, post, put } from '../../../common/services/ApiClientService';
import { Brand, BrandPost } from '../models/Brand';

const base = '/product/backoffice/brands';

export const getBrands = () => get<Brand[]>(base);

export const getBrand = (id: number) => get<Brand>(`${base}/${id}`);

export const createBrand = (brand: BrandPost) => post<Brand>(base, brand);

export const updateBrand = (id: number, brand: BrandPost) => put<null>(`${base}/${id}`, brand);

export const deleteBrand = (id: number) => del(`${base}/${id}`);
