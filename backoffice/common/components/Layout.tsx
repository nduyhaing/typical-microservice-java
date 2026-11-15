import Head from 'next/head';
import Link from 'next/link';
import { ReactNode } from 'react';
import Button from 'react-bootstrap/Button';
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import NavDropdown from 'react-bootstrap/NavDropdown';
import Navbar from 'react-bootstrap/Navbar';
import { getAuthentication, LOGOUT_URL } from '../../modules/auth/services/AuthenticationService';
import { useFetch } from '../hooks/useFetch';

type Props = { title?: string; children: ReactNode };

export type NavLink = { href: string; label: string; description: string };

/** Navigation grouped like YAS's sidebar (Catalog, Location, Tax, Inventory, Promotion); the dashboard lists the same pages. */
export const NAV_GROUPS: { label: string; links: NavLink[] }[] = [
  {
    label: 'Catalog',
    links: [
      { href: '/catalog/brands', label: 'Brands', description: 'Create, rename, publish and delete brands.' },
      { href: '/catalog/categories', label: 'Categories', description: 'Organise the category tree shown in the storefront.' },
      { href: '/catalog/products', label: 'Products', description: 'Products, prices, flags, options and variations.' },
      { href: '/reviews', label: 'Reviews', description: "Customers' product reviews: search and delete." },
    ],
  },
  {
    label: 'Sales',
    links: [
      {
        href: '/sales/orders',
        label: 'Orders',
        description: 'Orders: search, see the details, move them through payment, shipping, cancel or refund.',
      },
    ],
  },
  {
    label: 'Location',
    links: [
      { href: '/location/countries', label: 'Countries', description: 'Countries, their codes and address options.' },
      {
        href: '/location/state-or-provinces',
        label: 'States or provinces',
        description: 'States or provinces per country, and their districts.',
      },
    ],
  },
  {
    label: 'Tax',
    links: [
      { href: '/tax/tax-classes', label: 'Tax classes', description: 'Tax classes that products refer to.' },
      { href: '/tax/tax-rates', label: 'Tax rates', description: 'Rates per tax class, country, state and zip code.' },
    ],
  },
  {
    label: 'Inventory',
    links: [
      { href: '/inventory/warehouses', label: 'Warehouses', description: 'Warehouses and their addresses.' },
      {
        href: '/inventory/warehouse-stocks',
        label: 'Warehouse stocks',
        description: 'Stock per warehouse: adjust quantities with a note, see the history.',
      },
      {
        href: '/inventory/warehouse-products',
        label: 'Warehouse products',
        description: 'Add stock-tracked products to a warehouse.',
      },
    ],
  },
  {
    label: 'Promotion',
    links: [
      {
        href: '/promotion/manager-promotion',
        label: 'Promotions',
        description: 'Coupon codes: percentage or fixed discounts for products, brands or categories.',
      },
    ],
  },
  {
    label: 'System',
    links: [
      {
        href: '/webhook',
        label: 'Webhooks',
        description: 'Signed product and order event deliveries to external URLs, with their delivery log.',
      },
    ],
  },
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
            {NAV_GROUPS.map((group) => (
              <NavDropdown key={group.label} title={group.label} id={`nav-${group.label.toLowerCase()}`}>
                {group.links.map((link) => (
                  <NavDropdown.Item key={link.href} as={Link} href={link.href}>
                    {link.label}
                  </NavDropdown.Item>
                ))}
              </NavDropdown>
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
