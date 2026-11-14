import Alert from 'react-bootstrap/Alert';
import { toErrorInfo } from '../services/errors';

type Props = { error: unknown };

/** A failed API call: the server's message (ErrorVm `detail`) and its field errors, or a generic text. */
export default function ErrorMessage({ error }: Props) {
  const { message, fieldErrors } = toErrorInfo(error);
  return (
    <Alert variant="danger" role="alert">
      {message}
      {fieldErrors.length > 0 && (
        <ul className="mb-0 mt-2">
          {fieldErrors.map((fieldError) => (
            <li key={fieldError}>{fieldError}</li>
          ))}
        </ul>
      )}
    </Alert>
  );
}
