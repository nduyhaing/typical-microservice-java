import Alert from 'react-bootstrap/Alert';

type Props = { message?: string };

export default function ErrorMessage({ message = 'Something went wrong. Please try again later.' }: Props) {
  return (
    <Alert variant="danger" role="alert">
      {message}
    </Alert>
  );
}
