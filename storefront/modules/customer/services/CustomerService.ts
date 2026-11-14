import { get } from '../../../common/services/ApiClientService';
import { Customer } from '../models/Customer';

export const getMyProfile = () => get<Customer>('/customer/storefront/customer/profile');
