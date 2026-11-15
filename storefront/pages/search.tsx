import { GetServerSideProps } from 'next';
import { useRouter } from 'next/router';
import { FormEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Pagination from 'react-bootstrap/Pagination';
import Row from 'react-bootstrap/Row';
import ErrorMessage from '../common/components/ErrorMessage';
import Layout from '../common/components/Layout';
import ProductGrid from '../common/components/ProductGrid';
import { ProductThumbnail } from '../modules/catalog/models/ProductThumbnail';
import { ProductListGet, SearchParams, SortType } from '../modules/search/models/ProductSearch';
import { searchProducts } from '../modules/search/services/SearchService';
import { Filter, parseSearchQuery, toUrlQuery } from '../modules/search/utils/searchQuery';

type Props = { params: SearchParams; result: ProductListGet | null };

/** Page numbers shown around the current one. */
const PAGE_WINDOW = 2;

const FACETS: { filter: Filter; aggregation: string; label: string }[] = [
  { filter: 'brand', aggregation: 'brands', label: 'Brand' },
  { filter: 'category', aggregation: 'categories', label: 'Category' },
  { filter: 'attribute', aggregation: 'attributes', label: 'Attribute' },
];

const SORT_LABELS: Record<SortType, string> = {
  DEFAULT: 'Best match',
  PRICE_ASC: 'Price: low to high',
  PRICE_DESC: 'Price: high to low',
};

/**
 * Search results, rendered on the server from the URL (so a search URL can be shared and the back button works):
 * every filter, the price range, the sort and the page live in the query string. If the search service fails the
 * page still renders, with an error message instead of results.
 */
export const getServerSideProps: GetServerSideProps<Props> = async ({ query }) => {
  const params = parseSearchQuery(query);
  let result: ProductListGet | null;
  try {
    result = await searchProducts(params);
  } catch (error) {
    console.error('Search failed', error);
    result = null;
  }
  // Next refuses undefined in props
  return { props: { params: JSON.parse(JSON.stringify(params)) as SearchParams, result } };
};

const toThumbnail = (hit: ProductListGet['products'][number]): ProductThumbnail => ({
  id: hit.id,
  name: hit.name,
  slug: hit.slug,
  thumbnailUrl: hit.thumbnailUrl ?? '',
  price: hit.price ?? undefined,
});

function PriceFilter({ params, onApply }: { params: SearchParams; onApply: (min?: number, max?: number) => void }) {
  const [min, setMin] = useState(params.minPrice?.toString() ?? '');
  const [max, setMax] = useState(params.maxPrice?.toString() ?? '');
  const [invalid, setInvalid] = useState(false);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    const minPrice = min === '' ? undefined : Number(min);
    const maxPrice = max === '' ? undefined : Number(max);
    if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    onApply(minPrice, maxPrice);
  };

  return (
    <Form onSubmit={onSubmit} className="mb-4">
      <h2 className="h6">Price</h2>
      <div className="d-flex gap-2 mb-2">
        <Form.Control
          type="number"
          min={0}
          max={1_000_000_000}
          step="any"
          size="sm"
          placeholder="Min"
          aria-label="Min price"
          value={min}
          onChange={(event) => setMin(event.target.value)}
        />
        <Form.Control
          type="number"
          min={0}
          max={1_000_000_000}
          step="any"
          size="sm"
          placeholder="Max"
          aria-label="Max price"
          value={max}
          onChange={(event) => setMax(event.target.value)}
        />
      </div>
      {invalid && <p className="text-danger small mb-2">Min price must not be above max price.</p>}
      <Button type="submit" size="sm" variant="outline-secondary" aria-label="Apply price">
        Apply
      </Button>
    </Form>
  );
}

function Facet({
  label,
  counts,
  selected,
  onToggle,
}: {
  label: string;
  counts: Record<string, number>;
  selected: string[];
  onToggle: (value: string) => void;
}) {
  // selected values the current results don't count stay listed so they can be unchecked
  const entries: [string, number | undefined][] = [
    ...Object.entries(counts),
    ...selected.filter((value) => !(value in counts)).map((value): [string, undefined] => [value, undefined]),
  ];
  if (entries.length === 0) {
    return null;
  }
  return (
    <fieldset className="mb-4" aria-label={label}>
      <legend className="h6">{label}</legend>
      {entries.map(([value, count]) => (
        <Form.Check
          key={value}
          id={`facet-${label}-${value}`}
          type="checkbox"
          label={count === undefined ? value : `${value} (${count})`}
          checked={selected.includes(value)}
          onChange={() => onToggle(value)}
        />
      ))}
    </fieldset>
  );
}

function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (totalPages <= 1) {
    return null;
  }
  const from = Math.max(0, page - PAGE_WINDOW);
  const to = Math.min(totalPages - 1, page + PAGE_WINDOW);
  const pages = Array.from({ length: to - from + 1 }, (_, i) => from + i);
  return (
    <nav aria-label="Search result pages">
      <Pagination className="mt-4">
        <Pagination.Prev disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Previous page" />
        {pages.map((p) => (
          <Pagination.Item key={p} active={p === page} onClick={() => onPage(p)}>
            {p + 1}
          </Pagination.Item>
        ))}
        <Pagination.Next
          disabled={page >= totalPages - 1}
          onClick={() => onPage(page + 1)}
          aria-label="Next page"
        />
      </Pagination>
    </nav>
  );
}

export default function SearchPage({ params, result }: Props) {
  const router = useRouter();

  /** Every change goes through the URL; a filter change starts again at the first page. */
  const go = (changes: Partial<SearchParams>) => {
    router.push({ pathname: '/search', query: toUrlQuery({ ...params, page: 0, ...changes }) });
  };

  const toggle = (filter: Filter, value: string) => {
    const current = params[filter];
    go({ [filter]: current.includes(value) ? current.filter((v) => v !== value) : [...current, value] });
  };

  const title = params.keyword ? `Results for "${params.keyword}"` : 'All products';

  return (
    <Layout title={params.keyword ? `Search: ${params.keyword}` : 'Search'}>
      <Row className="g-4">
        <Col md={3}>
          {result && (
            <>
              {FACETS.map(({ filter, aggregation, label }) => (
                <Facet
                  key={filter}
                  label={label}
                  counts={result.aggregations?.[aggregation] ?? {}}
                  selected={params[filter]}
                  onToggle={(value) => toggle(filter, value)}
                />
              ))}
              {/* keyed by the URL values: navigating (back button) resets the inputs */}
              <PriceFilter
                key={`${params.minPrice ?? ''}-${params.maxPrice ?? ''}`}
                params={params}
                onApply={(minPrice, maxPrice) => go({ minPrice, maxPrice })}
              />
            </>
          )}
        </Col>
        <Col md={9}>
          <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
            <div>
              <h1 className="h3 mb-0">{title}</h1>
              {result && (
                <p className="text-muted small mb-0">
                  {result.totalElements} {result.totalElements === 1 ? 'product' : 'products'}
                </p>
              )}
            </div>
            {result && (
              <Form.Group controlId="search-sort" className="d-flex align-items-center gap-2">
                <Form.Label className="mb-0 small text-nowrap">Sort by</Form.Label>
                <Form.Select
                  size="sm"
                  value={params.sortType}
                  onChange={(event) => go({ sortType: event.target.value as SortType })}
                >
                  {Object.entries(SORT_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </Form.Select>
              </Form.Group>
            )}
          </div>
          {result ? (
            <>
              <ProductGrid products={result.products.map(toThumbnail)} />
              <Pager
                page={params.page}
                totalPages={result.totalPages}
                onPage={(page) => go({ ...params, page })}
              />
            </>
          ) : (
            <ErrorMessage message="Search is not available right now. Please try again later." />
          )}
        </Col>
      </Row>
    </Layout>
  );
}
