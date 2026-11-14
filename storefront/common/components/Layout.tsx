import Head from 'next/head';
import Link from 'next/link';
import { ReactNode } from 'react';
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';

type Props = { title?: string; children: ReactNode };

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
        </Container>
      </Navbar>
      <Container as="main" className="pb-5">
        {children}
      </Container>
      <footer className="border-top py-3 text-center text-muted small">shop_app storefront</footer>
    </>
  );
}
