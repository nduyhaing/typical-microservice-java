import Modal from 'react-bootstrap/Modal';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { useFetch } from '../../../common/hooks/useFetch';
import { StateOrProvince } from '../models/Location';
import { getDistricts } from '../services/StateOrProvinceService';

type Props = { state: StateOrProvince; onClose: () => void };

/** The districts of a state, read-only (reference data; there is no district admin API). */
export default function DistrictsModal({ state, onClose }: Props) {
  const { data: districts, error, loading } = useFetch(() => getDistricts(state.id), [state.id]);
  return (
    <Modal show onHide={onClose} scrollable>
      <Modal.Header closeButton>
        <Modal.Title as="h2" className="h5">
          Districts of {state.name}
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {loading && <p>Loading...</p>}
        {error && <ErrorMessage error={error} />}
        {districts && districts.length === 0 && <p className="mb-0">No districts.</p>}
        {districts && districts.length > 0 && (
          <ul className="mb-0">
            {districts.map((d) => (
              <li key={d.id}>{d.name}</li>
            ))}
          </ul>
        )}
      </Modal.Body>
    </Modal>
  );
}
