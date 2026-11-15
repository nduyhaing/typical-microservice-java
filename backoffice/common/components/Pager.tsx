import Button from 'react-bootstrap/Button';
import { PageInfo } from '../models/Page';

type Props = {
  page: PageInfo;
  /** What is counted, plural ("countries"). */
  noun: string;
  onChange: (pageNo: number) => void;
};

/** Previous/next paging under a list (like the products list). */
export default function Pager({ page, noun, onChange }: Props) {
  return (
    <div className="d-flex align-items-center gap-3">
      <Button variant="outline-secondary" size="sm" disabled={page.pageNo === 0} onClick={() => onChange(page.pageNo - 1)}>
        Previous
      </Button>
      <span>
        Page {page.totalPages === 0 ? 0 : page.pageNo + 1} of {page.totalPages} ({page.totalElements} {noun})
      </span>
      <Button variant="outline-secondary" size="sm" disabled={page.isLast} onClick={() => onChange(page.pageNo + 1)}>
        Next
      </Button>
    </div>
  );
}
