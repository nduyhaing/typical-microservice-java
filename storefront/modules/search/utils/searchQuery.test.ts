import { PAGE_SIZE, parseSearchQuery, toApiQuery, toUrlQuery } from './searchQuery';

describe('parseSearchQuery', () => {
  it('reads every parameter from the URL', () => {
    expect(
      parseSearchQuery({
        keyword: ' galaxy ',
        page: '2',
        brand: ['Samsung', 'Apple'],
        category: 'Phones',
        attribute: '128GB',
        minPrice: '100',
        maxPrice: '999.5',
        sortType: 'PRICE_DESC',
      })
    ).toEqual({
      keyword: 'galaxy',
      page: 2,
      brand: ['Samsung', 'Apple'],
      category: ['Phones'],
      attribute: ['128GB'],
      minPrice: 100,
      maxPrice: 999.5,
      sortType: 'PRICE_DESC',
    });
  });

  it('defaults and drops what the search service would reject', () => {
    expect(
      parseSearchQuery({
        keyword: 'x'.repeat(150),
        page: '-3',
        brand: ['', 'Samsung', 'Samsung', 'a,b', 'y'.repeat(101)],
        minPrice: 'abc',
        maxPrice: '-1',
        sortType: 'DROP TABLE',
      })
    ).toEqual({
      keyword: 'x'.repeat(100),
      page: 0,
      brand: ['Samsung'],
      category: [],
      attribute: [],
      minPrice: undefined,
      maxPrice: undefined,
      sortType: 'DEFAULT',
    });
  });

  it('keeps the page inside the 10 000 result window and at most 20 values per filter', () => {
    const many = Array.from({ length: 30 }, (_, i) => `b${i}`);
    const parsed = parseSearchQuery({ page: '99999', brand: many, maxPrice: '5000000000' });
    expect((parsed.page + 1) * PAGE_SIZE).toBeLessThanOrEqual(10_000);
    expect(parsed.brand).toHaveLength(20);
    expect(parsed.maxPrice).toBe(1_000_000_000);
  });
});

describe('toApiQuery', () => {
  it('encodes the YAS parameters, filters comma-separated', () => {
    const query = toApiQuery(
      parseSearchQuery({ keyword: 'a&b=c', brand: ['Samsung', 'Apple & Co'], minPrice: '10', page: '1' })
    );
    const params = new URLSearchParams(query);
    expect(params.get('keyword')).toBe('a&b=c');
    expect(params.get('brand')).toBe('Samsung,Apple & Co');
    expect(params.get('minPrice')).toBe('10');
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe(String(PAGE_SIZE));
    expect(params.get('sortType')).toBe('DEFAULT');
    expect(params.has('category')).toBe(false);
    expect(params.has('maxPrice')).toBe(false);
    expect(query).not.toContain('&b=c');
  });
});

describe('toUrlQuery', () => {
  it('leaves out defaults so URLs stay short', () => {
    expect(toUrlQuery(parseSearchQuery({ keyword: 'galaxy' }))).toEqual({ keyword: 'galaxy' });
  });

  it('round-trips through parseSearchQuery', () => {
    const params = parseSearchQuery({
      keyword: 'galaxy',
      page: '3',
      brand: ['Samsung', 'Apple'],
      minPrice: '0',
      maxPrice: '20',
      sortType: 'PRICE_ASC',
    });
    expect(parseSearchQuery(toUrlQuery(params))).toEqual(params);
  });
});
