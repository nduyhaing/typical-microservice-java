import { del, get, post, put } from '../../../common/services/ApiClientService';
import { Category, CategoryDetail, CategoryPost } from '../models/Category';

const base = '/product/backoffice/categories';

export const getCategories = () => get<Category[]>(base);

export const getCategory = (id: number) => get<CategoryDetail>(`${base}/${id}`);

export const createCategory = (category: CategoryPost) => post<CategoryDetail>(base, category);

export const updateCategory = (id: number, category: CategoryPost) => put<null>(`${base}/${id}`, category);

export const deleteCategory = (id: number) => del(`${base}/${id}`);
