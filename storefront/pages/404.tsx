import Link from 'next/link';
import Layout from '../common/components/Layout';

export default function NotFoundPage() {
  return (
    <Layout title="Page not found">
      <h1 className="h3">Page not found</h1>
      <p>The page or product you are looking for does not exist.</p>
      <Link href="/products">Browse all products</Link>
    </Layout>
  );
}
