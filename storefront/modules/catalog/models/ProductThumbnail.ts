export type ProductThumbnail = {
  id: number;
  name: string;
  slug: string;
  thumbnailUrl: string;
  /** absent on brand listings (YAS ProductThumbnailVm has no price) */
  price?: number;
  /** decisions.md 3c-24 (list-featured and the other thumbnail lists) */
  stockTrackingEnabled?: boolean;
  stockAvailable?: boolean;
  stockQuantity?: number | null;
};
