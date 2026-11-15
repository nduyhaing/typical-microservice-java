import { ChangeEvent, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { UploadedImage, uploadImage } from '../services/MediaService';

type Props = {
  /** Input id (labels the file input). */
  id: string;
  label: string;
  /** Current images; a single-image control holds at most one. */
  images: UploadedImage[];
  /** Several images (gallery) or one (thumbnail, category image: a new upload replaces it). */
  multiple?: boolean;
  onChange: (images: UploadedImage[]) => void;
};

const ACCEPT = 'image/png,image/jpeg,image/gif';

/** Image upload with previews and remove buttons; uploads go to the media service as soon as files are chosen. */
export default function ImageUpload({ id, label, images, multiple = false, onChange }: Props) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<unknown>();

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const files = Array.from(input.files ?? []);
    if (files.length === 0) {
      return;
    }
    setError(undefined);
    setUploading(true);
    const uploaded: UploadedImage[] = [];
    try {
      for (const file of multiple ? files : files.slice(0, 1)) {
        uploaded.push(await uploadImage(file));
      }
    } catch (e) {
      setError(e);
    } finally {
      setUploading(false);
      input.value = '';
    }
    if (uploaded.length > 0) {
      onChange(multiple ? [...images, ...uploaded] : uploaded);
    }
  }

  return (
    <Form.Group className="mb-3" controlId={id}>
      <Form.Label>{label}</Form.Label>
      {error !== undefined && <ErrorMessage error={error} />}
      <div className="d-flex flex-wrap gap-2 mb-2">
        {images.map((image) => (
          <div key={image.id} className="border rounded p-1 text-center" style={{ width: 120 }}>
            {image.url ? (
              // eslint-disable-next-line @next/next/no-img-element -- media URLs are absolute, served by the media service
              <img src={image.url} alt={`Image ${image.id}`} style={{ maxWidth: '100%', height: 90, objectFit: 'contain' }} />
            ) : (
              <div className="text-muted small" style={{ height: 90 }}>
                Image {image.id}
              </div>
            )}
            <Button
              size="sm"
              variant="outline-danger"
              className="mt-1"
              aria-label={`Remove image ${image.id}`}
              onClick={() => onChange(images.filter((i) => i.id !== image.id))}
            >
              Remove
            </Button>
          </div>
        ))}
      </div>
      <Form.Control type="file" accept={ACCEPT} multiple={multiple} disabled={uploading} onChange={upload} />
      {uploading && <Form.Text>Uploading...</Form.Text>}
    </Form.Group>
  );
}
