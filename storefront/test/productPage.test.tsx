import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReactNode } from 'react';
import { vi } from 'vitest';
import { ProductDetail } from '../modules/catalog/models/ProductDetail';
import ProductPage from '../pages/products/[slug]';

vi.mock('../common/components/Layout', () => ({ default: ({ children }: { children: ReactNode }) => <>{children}</> }));
vi.mock('../modules/catalog/components/RelatedProducts', () => ({ default: () => null }));
vi.mock('../modules/cart/services/CartService');

const product = (id: number, name: string, images: string[]) =>
  ({
    id,
    name,
    brandName: null,
    productCategories: [],
    productAttributeGroups: [],
    shortDescription: null,
    description: null,
    specification: null,
    isAllowedToOrder: true,
    isPublished: true,
    isFeatured: false,
    hasOptions: false,
    price: 10,
    thumbnailMediaUrl: images[0] ?? '',
    productImageMediaUrls: images.slice(1),
    stockTrackingEnabled: false,
    stockAvailable: true,
    stockQuantity: null,
  }) as ProductDetail;

describe('product page', () => {
  it('navigating to another product resets the gallery pick and the quantity', async () => {
    const first = product(1, 'Phone', ['http://m/1a.png', 'http://m/1b.png']);
    const second = product(2, 'Tablet', ['http://m/2a.png', 'http://m/2b.png']);
    const { rerender } = render(<ProductPage product={first} options={[]} variations={[]} />);

    await userEvent.click(screen.getByRole('button', { name: 'Show image 2' }));
    expect(screen.getByRole('img', { name: 'Phone' })).toHaveAttribute('src', 'http://m/1b.png');
    await userEvent.clear(screen.getByLabelText('Quantity'));
    await userEvent.type(screen.getByLabelText('Quantity'), '3');

    rerender(<ProductPage product={second} options={[]} variations={[]} />);

    expect(screen.getByRole('img', { name: 'Tablet' })).toHaveAttribute('src', 'http://m/2a.png');
    expect(screen.getByLabelText('Quantity')).toHaveValue(1);
  });
});
