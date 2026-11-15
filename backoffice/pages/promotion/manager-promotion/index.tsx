import Link from 'next/link';
import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import Pager from '../../../common/components/Pager';
import { useDeleteAction } from '../../../common/hooks/useDeleteAction';
import { useFetch } from '../../../common/hooks/useFetch';
import { formatPrice } from '../../../common/utils/format';
import { PromotionDetail, PromotionFilter } from '../../../modules/promotion/models/Promotion';
import { deletePromotion, getPromotions } from '../../../modules/promotion/services/PromotionService';
import { fromLocalInput } from '../../../modules/promotion/utils/dates';

const NO_FILTER: PromotionFilter = { promotionName: '', couponCode: '', startDate: '', endDate: '' };
const TARGETS = { PRODUCT: 'Products', BRAND: 'Brands', CATEGORY: 'Categories' } as const;

function discount(p: PromotionDetail): string {
  return p.discountType === 'FIXED' ? formatPrice(p.discountAmount ?? 0) : `${p.discountPercentage}%`;
}

function targetCount(p: PromotionDetail): number {
  if (p.applyTo === 'BRAND') {
    return p.brandIds.length;
  }
  return p.applyTo === 'CATEGORY' ? p.categoryIds.length : p.productIds.length;
}

const when = (iso: string) => new Date(iso).toLocaleString();

/** Promotions (YAS manager-promotion): filter by name, coupon code and dates; edit; delete unused ones. */
export default function PromotionsPage() {
  const [pageNo, setPageNo] = useState(0);
  const [version, setVersion] = useState(0);
  const [filter, setFilter] = useState<PromotionFilter>(NO_FILTER);
  const [draft, setDraft] = useState({ promotionName: '', couponCode: '', from: '', until: '' });
  const { data, error, loading } = useFetch(() => getPromotions(pageNo, filter), [pageNo, filter, version]);
  const deletion = useDeleteAction('promotion', () => setVersion((v) => v + 1));

  function search(event: FormEvent) {
    event.preventDefault();
    setPageNo(0);
    setFilter({
      promotionName: draft.promotionName.trim(),
      couponCode: draft.couponCode.trim(),
      startDate: fromLocalInput(draft.from),
      endDate: fromLocalInput(draft.until),
    });
  }

  return (
    <Layout title="Promotions">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 m-0">Promotions</h1>
        <Link href="/promotion/manager-promotion/create" className="btn btn-primary">
          Create promotion
        </Link>
      </div>
      <Form onSubmit={search} className="mb-3">
        <Row className="g-2 align-items-end">
          <Form.Group as={Col} md={3} controlId="promotion-filter-name">
            <Form.Label>Name</Form.Label>
            <Form.Control
              value={draft.promotionName}
              maxLength={450}
              onChange={(e) => setDraft({ ...draft, promotionName: e.target.value })}
            />
          </Form.Group>
          <Form.Group as={Col} md={3} controlId="promotion-filter-code">
            <Form.Label>Coupon code</Form.Label>
            <Form.Control
              value={draft.couponCode}
              maxLength={100}
              onChange={(e) => setDraft({ ...draft, couponCode: e.target.value })}
            />
          </Form.Group>
          <Form.Group as={Col} md={2} controlId="promotion-filter-from">
            <Form.Label>Running from</Form.Label>
            <Form.Control type="datetime-local" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
          </Form.Group>
          <Form.Group as={Col} md={2} controlId="promotion-filter-until">
            <Form.Label>Running until</Form.Label>
            <Form.Control type="datetime-local" value={draft.until} onChange={(e) => setDraft({ ...draft, until: e.target.value })} />
          </Form.Group>
          <Col md={2}>
            <Button type="submit" variant="outline-primary">
              Search
            </Button>
          </Col>
        </Row>
      </Form>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.promotionDetailVmList.length === 0 && <p>No promotions found.</p>}
      {data && data.promotionDetailVmList.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Name</th>
              <th>Coupon code</th>
              <th>Discount</th>
              <th>Applies to</th>
              <th>Usage</th>
              <th>Status</th>
              <th>Runs</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.promotionDetailVmList.map((p) => (
              <tr key={p.id}>
                <td>{p.name}</td>
                <td>
                  <code>{p.couponCode}</code>
                </td>
                <td>{discount(p)}</td>
                <td>
                  {TARGETS[p.applyTo]} ({targetCount(p)})
                </td>
                <td>{`${p.usageCount} / ${p.usageType === 'LIMITED' ? p.usageLimit : 'unlimited'}`}</td>
                <td>{p.isActive ? 'Active' : 'Inactive'}</td>
                <td className="small">
                  {when(p.startDate)} – {when(p.endDate)}
                </td>
                <td className="text-end text-nowrap">
                  <Link href={`/promotion/manager-promotion/${p.id}/edit`} className="btn btn-sm btn-outline-primary">
                    Edit
                  </Link>{' '}
                  <Button size="sm" variant="outline-danger" onClick={() => deletion.remove(p.name, () => deletePromotion(p.id))}>
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="promotions" onChange={setPageNo} />}
    </Layout>
  );
}
