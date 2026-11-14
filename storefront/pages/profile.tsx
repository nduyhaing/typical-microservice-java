import Card from 'react-bootstrap/Card';
import Spinner from 'react-bootstrap/Spinner';
import ErrorMessage from '../common/components/ErrorMessage';
import Layout from '../common/components/Layout';
import { useFetch } from '../common/hooks/useFetch';
import { ApiError } from '../common/services/ApiClientService';
import { LOGIN_URL } from '../modules/auth/services/AuthenticationService';
import { getMyProfile } from '../modules/customer/services/CustomerService';

/** The logged-in customer's profile from Keycloak (via the customer service). */
export default function ProfilePage() {
  const { data: profile, error, loading } = useFetch(getMyProfile);
  const notLoggedIn = error instanceof ApiError && (error.status === 401 || error.status === 403);

  return (
    <Layout title="Profile">
      <h1 className="h3 mb-3">Profile</h1>
      {loading && <Spinner animation="border" role="status" aria-label="Loading" />}
      {notLoggedIn && <a href={LOGIN_URL}>Log in to see your profile</a>}
      {error && !notLoggedIn && <ErrorMessage />}
      {profile && (
        <Card style={{ maxWidth: 480 }}>
          <Card.Body>
            <Card.Title>
              {profile.firstName} {profile.lastName}
            </Card.Title>
            <dl className="row mb-0">
              <dt className="col-4">Username</dt>
              <dd className="col-8">{profile.username}</dd>
              <dt className="col-4">Email</dt>
              <dd className="col-8">{profile.email}</dd>
            </dl>
          </Card.Body>
        </Card>
      )}
    </Layout>
  );
}
