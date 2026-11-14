import { slugify } from './slug';

describe('slugify', () => {
  it('lower-cases and joins words with dashes', () => {
    expect(slugify('  Galaxy S24 Ultra ')).toBe('galaxy-s24-ultra');
  });

  it('drops accents and punctuation', () => {
    expect(slugify('Điện thoại & Phụ kiện!')).toBe('dien-thoai-phu-kien');
  });
});
