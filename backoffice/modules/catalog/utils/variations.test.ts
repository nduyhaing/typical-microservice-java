import {
  combineOptionValues,
  generateVariations,
  optionsFromVariations,
  parseValues,
  toProductOptionValueDisplays,
  toProductOptionValues,
} from './variations';

const color = { id: 1, name: 'Color', values: ['Red', 'Blue'] };
const size = { id: 2, name: 'Size', values: ['S', 'M'] };

describe('parseValues', () => {
  it('splits on commas, trims, drops empties and duplicates', () => {
    expect(parseValues(' Red, Blue ,,Red ')).toEqual(['Red', 'Blue']);
  });
});

describe('combineOptionValues', () => {
  it('builds every combination of the option values', () => {
    expect(combineOptionValues([color, size])).toEqual([
      { 1: 'Red', 2: 'S' },
      { 1: 'Red', 2: 'M' },
      { 1: 'Blue', 2: 'S' },
      { 1: 'Blue', 2: 'M' },
    ]);
  });

  it('is empty while an option has no values or no option is chosen', () => {
    expect(combineOptionValues([color, { ...size, values: [] }])).toEqual([]);
    expect(combineOptionValues([])).toEqual([]);
  });
});

describe('generateVariations', () => {
  it('names each variation after the product and its values, with unique slug and sku', () => {
    expect(generateVariations('Tee', 'TEE', 20, [color], [])).toEqual([
      { id: null, name: 'Tee Red', slug: 'tee-red', sku: 'TEE-red', gtin: '', price: 20, thumbnailMediaId: null, productImageIds: [], optionValuesByOptionId: { 1: 'Red' } },
      { id: null, name: 'Tee Blue', slug: 'tee-blue', sku: 'TEE-blue', gtin: '', price: 20, thumbnailMediaId: null, productImageIds: [], optionValuesByOptionId: { 1: 'Blue' } },
    ]);
  });

  it('keeps existing variations (and their edits) and only adds missing combinations', () => {
    const existing = [
      { id: 5, name: 'Tee Red', slug: 'tee-red', sku: 'X1', gtin: '', price: 25, thumbnailMediaId: 4, productImageIds: [], optionValuesByOptionId: { 1: 'Red' } },
    ];
    const result = generateVariations('Tee', 'TEE', 20, [{ ...color, values: ['Red', 'Green'] }], existing);
    expect(result).toHaveLength(2);
    expect(result[0]).toBe(existing[0]);
    expect(result[1]).toMatchObject({ id: null, name: 'Tee Green', price: 20, optionValuesByOptionId: { 1: 'Green' } });
  });
});

describe('toProductOptionValues', () => {
  it('lists, per option, the values the variations use (ProductOptionValuePostVm)', () => {
    const variations = generateVariations('Tee', 'TEE', 20, [color, size], []);
    expect(toProductOptionValues(variations)).toEqual([
      { productOptionId: 1, displayType: 'text', displayOrder: 1, value: ['Red', 'Blue'] },
      { productOptionId: 2, displayType: 'text', displayOrder: 2, value: ['S', 'M'] },
    ]);
  });

  it('display values are YAS JSON objects of value to colour', () => {
    expect(
      toProductOptionValueDisplays([{ productOptionId: 1, displayType: 'text', displayOrder: 1, value: ['Red', 'Blue'] }])
    ).toEqual([{ productOptionId: 1, displayType: 'text', displayOrder: 1, value: '{"Red":"#000000","Blue":"#000000"}' }]);
  });
});

describe('optionsFromVariations', () => {
  it('rebuilds the chosen options and their values from existing variations', () => {
    const options = [
      { id: 1, name: 'Color' },
      { id: 2, name: 'Size' },
    ];
    expect(
      optionsFromVariations(
        [
          { id: 7, name: 'Tee Red S', slug: 'tee-red-s', sku: 'a', gtin: null, price: 1, thumbnail: null, productImages: null, options: { '1': 'Red', '2': 'S' } },
          { id: 8, name: 'Tee Blue S', slug: 'tee-blue-s', sku: 'b', gtin: null, price: 1, thumbnail: null, productImages: null, options: { '1': 'Blue', '2': 'S' } },
        ],
        options
      )
    ).toEqual([
      { id: 1, name: 'Color', values: ['Red', 'Blue'] },
      { id: 2, name: 'Size', values: ['S'] },
    ]);
  });
});
