import Link from 'next/link';
import Card from 'react-bootstrap/Card';
import Col from 'react-bootstrap/Col';
import Row from 'react-bootstrap/Row';
import Layout, { NAV_GROUPS } from '../common/components/Layout';
import LatestRatings from '../modules/home/components/LatestRatings';
import SampleDataCard from '../modules/sampledata/components/SampleDataCard';

export default function HomePage() {
  return (
    <Layout title="Dashboard">
      <h1 className="h3 mb-4">Back office</h1>
      <LatestRatings />
      {NAV_GROUPS.map((group) => {
        const headingId = `dashboard-${group.label.toLowerCase()}`;
        return (
          <section key={group.label} aria-labelledby={headingId} className="mb-4">
            <h2 id={headingId} className="h5 mb-3">
              {group.label}
            </h2>
            <Row xs={1} md={3} className="g-3">
              {group.links.map((link) => (
                <Col key={link.href}>
                  <Card className="h-100">
                    <Card.Body>
                      <Card.Title>
                        <Link href={link.href}>{link.label}</Link>
                      </Card.Title>
                      <Card.Text>{link.description}</Card.Text>
                    </Card.Body>
                  </Card>
                </Col>
              ))}
            </Row>
          </section>
        );
      })}
      <SampleDataCard />
    </Layout>
  );
}
