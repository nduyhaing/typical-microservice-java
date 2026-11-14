import Link from 'next/link';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Layout, { NAV_LINKS } from '../common/components/Layout';

const DESCRIPTIONS: Record<string, string> = {
  '/catalog/brands': 'Create, rename, publish and delete brands.',
  '/catalog/categories': 'Organise the category tree shown in the storefront.',
  '/catalog/products': 'Products, prices, flags, options and variations.',
};

export default function HomePage() {
  return (
    <Layout title="Dashboard">
      <h1 className="h3 mb-4">Back office</h1>
      <Row xs={1} md={3} className="g-3">
        {NAV_LINKS.map((link) => (
          <Col key={link.href}>
            <Card className="h-100">
              <Card.Body>
                <Card.Title>
                  <Link href={link.href}>{link.label}</Link>
                </Card.Title>
                <Card.Text>{DESCRIPTIONS[link.href]}</Card.Text>
              </Card.Body>
            </Card>
          </Col>
        ))}
      </Row>
    </Layout>
  );
}
