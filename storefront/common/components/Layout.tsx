import Head from 'next/head';
import Link from 'next/link';
import { ReactNode } from 'react';
import Button from 'react-bootstrap/Button';
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import { LOGIN_URL, LOGOUT_URL } from '../../modules/auth/services/AuthenticationService';
import { getCartItemCount } from '../../modules/cart/services/CartService';
import SearchBox from '../../modules/search/components/SearchBox';
import { useAuth } from '../hooks/useAuth';
import { useFetch } from '../hooks/useFetch';

type Props = { title?: string; children: ReactNode };

/** Number of items in the cart; hidden while unknown or if the cart can't be read. */
function CartLink() {
  const { data: count } = useFetch(getCartItemCount);
  return (
    <Nav.Link as={Link} href="/cart">
      Cart{count ? ` (${count})` : ''}
    </Nav.Link>
  );
}

function AccountMenu() {
  const { authentication, loading } = useAuth();
  if (loading) {
    return null;
  }
  if (!authentication?.isAuthenticated) {
    return (
      <Nav>
        {/* a server route on the BFF, so a plain link instead of next/link */}
        <Nav.Link href={LOGIN_URL}>Log in</Nav.Link>
      </Nav>
    );
  }
  return (
    <Nav className="align-items-md-center gap-md-2">
      <Navbar.Text className="text-light">{authentication.authenticatedUser?.username}</Navbar.Text>
      <CartLink />
      <Nav.Link as={Link} href="/my-orders">
        My orders
      </Nav.Link>
      <Nav.Link as={Link} href="/profile">
        Profile
      </Nav.Link>
      <Nav.Link as={Link} href="/address">
        Addresses
      </Nav.Link>
      <form action={LOGOUT_URL} method="post" className="d-inline">
        <Button type="submit" variant="outline-light" size="sm">
          Log out
        </Button>
      </form>
    </Nav>
  );
}

export default function Layout({ title, children }: Props) {
  const pageTitle = title ? `${title} | shop_app` : 'shop_app';
  return (
    <>
      <Head>
        <title>{pageTitle}</title>
      </Head>
      <Navbar bg="dark" variant="dark" expand="md" className="mb-4">
        <Container>
          <Navbar.Brand as={Link} href="/">
            shop_app
          </Navbar.Brand>
          <Nav className="me-auto">
            <Nav.Link as={Link} href="/products">
              Products
            </Nav.Link>
          </Nav>
          <SearchBox />
          <AccountMenu />
        </Container>
      </Navbar>
      <Container as="main" className="pb-5">
        {children}
      </Container>
      <footer className="border-top py-3 text-center text-muted small">shop_app storefront</footer>
    </>
  );
}
