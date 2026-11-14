import { ProductVariation } from '../models/ProductVariation';
import { findVariation } from './variation';

const variation = (id: number, price: number, options: Record<number, string>): ProductVariation => ({
  id,
  name: `v${id}`,
  slug: `v${id}`,
  sku: '',
  gtin: '',
  price,
  thumbnail: null,
  productImages: [],
  options,
});

describe('findVariation', () => {
  const color = [variation(1, 799, { 1: 'Black' }), variation(2, 819, { 1: 'Violet' })];

  it('returns the variation matching the selected option value', () => {
    expect(findVariation(color, { 1: 'Violet' })?.price).toBe(819);
  });

  it('returns undefined when no variation has the selected value', () => {
    expect(findVariation(color, { 1: 'Red' })).toBeUndefined();
  });

  it('returns undefined for an empty selection', () => {
    expect(findVariation(color, {})).toBeUndefined();
  });

  it('requires every option of the variation to match', () => {
    const colorAndStorage = [
      variation(3, 899, { 1: 'Black', 2: '256 GB' }),
      variation(4, 999, { 1: 'Black', 2: '512 GB' }),
    ];
    expect(findVariation(colorAndStorage, { 1: 'Black' })).toBeUndefined();
    expect(findVariation(colorAndStorage, { 1: 'Black', 2: '512 GB' })?.price).toBe(999);
  });
});
