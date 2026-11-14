import Head from 'next/head';
import Link from 'next/link';
import { ReactNode } from 'react';
import Button from 'react-bootstrap/Button';
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import { getAuthentication, LOGOUT_URL } from '../../modules/auth/services/AuthenticationService';
import { useFetch } from '../hooks/useFetch';

type Props = { title?: string; children: ReactNode };

export const NAV_LINKS = [
  { href: '/catalog/brands', label: 'Brands' },
  { href: '/catalog/categories', label: 'Categories' },
  { href: '/catalog/products', label: 'Products' },
];

function AccountMenu() {
  const { data: authentication } = useFetch(getAuthentication);
  if (!authentication?.isAuthenticated) {
    // backoffice-bff only lets logged-in admins reach these pages; nothing to offer while unknown
    return null;
  }
  return (
    <Nav className="align-items-md-center gap-md-2">
      <Navbar.Text className="text-light">{authentication.authenticatedUser?.username}</Navbar.Text>
      <form action={LOGOUT_URL} method="post" className="d-inline">
        <Button type="submit" variant="outline-light" size="sm">
          Log out
        </Button>
      </form>
    </Nav>
  );
}

export default function Layout({ title, children }: Props) {
  const pageTitle = title ? `${title} | shop_app back office` : 'shop_app back office';
  return (
    <>
      <Head>
        <title>{pageTitle}</title>
      </Head>
      <Navbar bg="dark" variant="dark" expand="md" className="mb-4">
        <Container>
          <Navbar.Brand as={Link} href="/">
            shop_app back office
          </Navbar.Brand>
          <Nav className="me-auto">
            {NAV_LINKS.map((link) => (
              <Nav.Link key={link.href} as={Link} href={link.href}>
                {link.label}
              </Nav.Link>
            ))}
          </Nav>
          <AccountMenu />
        </Container>
      </Navbar>
      <Container as="main" className="pb-5">
        {children}
      </Container>
    </>
  );
}
