import { get, postForm } from '../../../common/services/ApiClientService';

/** An uploaded image as the forms keep it (ImageVm shape: id + public URL for the preview). */
export type UploadedImage = { id: number; url: string };

/** MediaVm of the media service. */
type MediaVm = { id: number; caption: string; fileName: string; mediaType: string; url: string };

const base = '/media/medias';

/**
 * Uploads an image (`POST /api/media/medias`, multipart fields `multipartFile` and `caption`, ADMIN) and returns its id
 * with the public URL. The upload answers NoFileMediaVm without a URL, so the URL comes from `GET /medias/{id}`.
 */
export async function uploadImage(file: File, caption = file.name): Promise<UploadedImage> {
  const form = new FormData();
  form.append('multipartFile', file);
  form.append('caption', caption.slice(0, 255));
  const created = await postForm<{ id: number }>(base, form);
  const media = await get<MediaVm>(`${base}/${created.id}`);
  return { id: media.id, url: media.url };
}
