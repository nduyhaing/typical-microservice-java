export type ProductThumbnail = {
  id: number;
  name: string;
  slug: string;
  thumbnailUrl: string;
  /** absent on brand listings (YAS ProductThumbnailVm has no price) */
  price?: number;
};
