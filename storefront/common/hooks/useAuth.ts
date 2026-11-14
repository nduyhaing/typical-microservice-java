import { getAuthentication } from '../../modules/auth/services/AuthenticationService';
import { useFetch } from './useFetch';

export function useAuth() {
  const { data, loading } = useFetch(getAuthentication);
  return { authentication: data, loading };
}
