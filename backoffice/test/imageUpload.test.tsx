import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import ImageUpload from '../modules/media/components/ImageUpload';
import * as mediaService from '../modules/media/services/MediaService';
import { UploadedImage } from '../modules/media/services/MediaService';

vi.mock('../modules/media/services/MediaService');

/** The file dialog's "All files" choice lets any file through the input's accept filter. */
const user = () => userEvent.setup({ applyAccept: false });

const file = (name: string, type: string, size = 1) => new File([new Uint8Array(size)], name, { type });

afterEach(() => {
  vi.resetAllMocks();
});

function renderUpload(images: UploadedImage[] = [], multiple = true) {
  const onChange = vi.fn();
  render(<ImageUpload id="gallery" label="Product images" images={images} multiple={multiple} onChange={onChange} />);
  return onChange;
}

describe('ImageUpload client-side checks', () => {
  it('refuses a file over 10 MB without uploading it', async () => {
    const onChange = renderUpload();

    await user().upload(screen.getByLabelText('Product images'), file('huge.png', 'image/png', 10 * 1024 * 1024 + 1));

    expect(await screen.findByRole('alert')).toHaveTextContent('huge.png is too large (max 10 MB).');
    expect(mediaService.uploadImage).not.toHaveBeenCalled();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('refuses a file that is not PNG, JPEG or GIF without uploading any of the chosen files', async () => {
    renderUpload();

    await user().upload(screen.getByLabelText('Product images'), [
      file('ok.png', 'image/png'),
      file('promo.webp', 'image/webp'),
    ]);

    expect(await screen.findByRole('alert')).toHaveTextContent('promo.webp is not a PNG, JPEG or GIF image.');
    expect(mediaService.uploadImage).not.toHaveBeenCalled();
  });

  it('accepts a 10 MB JPEG and clears an earlier refusal', async () => {
    vi.mocked(mediaService.uploadImage).mockResolvedValue({ id: 5, url: 'http://m/5.jpg' });
    const onChange = renderUpload();

    await user().upload(screen.getByLabelText('Product images'), file('doc.txt', 'text/plain'));
    await screen.findByRole('alert');
    await user().upload(screen.getByLabelText('Product images'), file('photo.jpg', 'image/jpeg', 10 * 1024 * 1024));

    await waitFor(() => expect(onChange).toHaveBeenCalledWith([{ id: 5, url: 'http://m/5.jpg' }]));
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('ImageUpload while uploading', () => {
  it('disables Remove until the upload has finished', async () => {
    let finish: (image: UploadedImage) => void = () => undefined;
    vi.mocked(mediaService.uploadImage).mockImplementation(
      () =>
        new Promise<UploadedImage>((resolve) => {
          finish = resolve;
        })
    );
    const onChange = renderUpload([{ id: 1, url: 'http://m/1.png' }]);

    await user().upload(screen.getByLabelText('Product images'), file('new.png', 'image/png'));

    expect(await screen.findByText('Uploading...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Remove image 1' })).toBeDisabled();

    await act(async () => finish({ id: 2, url: 'http://m/2.png' }));

    expect(screen.getByRole('button', { name: 'Remove image 1' })).toBeEnabled();
    expect(onChange).toHaveBeenCalledWith([
      { id: 1, url: 'http://m/1.png' },
      { id: 2, url: 'http://m/2.png' },
    ]);
  });
});
