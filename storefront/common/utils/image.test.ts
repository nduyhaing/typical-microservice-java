import { imageOrPlaceholder, PLACEHOLDER_IMAGE } from './image';

describe('imageOrPlaceholder', () => {
  it.each(['', null, undefined])('returns the placeholder for %s', (url) => {
    expect(imageOrPlaceholder(url)).toBe(PLACEHOLDER_IMAGE);
  });

  it('keeps a real URL', () => {
    expect(imageOrPlaceholder('http://x/a.png')).toBe('http://x/a.png');
  });

  it('placeholder is the bundled default image', () => {
    expect(PLACEHOLDER_IMAGE).toBe('/static/images/default.png');
  });
});
