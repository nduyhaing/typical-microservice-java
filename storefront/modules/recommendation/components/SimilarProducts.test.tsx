import { render, screen } from '@testing-library/react';
import SimilarProducts from './SimilarProducts';

const phone = (id: number, name: string) => ({
  id,
  name,
  slug: name.toLowerCase().replace(/ /g, '-'),
  price: 449,
  brandName: 'Samsung',
  thumbnailUrl: null,
});

describe('SimilarProducts', () => {
  it('shows a "Similar products" strip of product cards linking to their pages', () => {
    render(<SimilarProducts products={[phone(2, 'Galaxy A55'), phone(3, 'iPhone 16')]} />);

    expect(screen.getByRole('heading', { name: 'Similar products' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Galaxy A55' })[0]).toHaveAttribute('href', '/products/galaxy-a55');
    expect(screen.getAllByRole('link', { name: 'iPhone 16' })[0]).toHaveAttribute('href', '/products/iphone-16');
    expect(screen.getAllByText('$449.00').length).toBeGreaterThan(0);
  });

  it('renders nothing when there are no similar products', () => {
    const { container } = render(<SimilarProducts products={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});
