/** BrandVm (YAS). */
export type Brand = {
  id: number;
  name: string;
  slug: string;
  isPublish: boolean;
};

/** BrandPostVm (YAS): create and update body. */
export type BrandPost = {
  name: string;
  slug: string;
  isPublish: boolean;
};
