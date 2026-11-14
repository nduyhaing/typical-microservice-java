type ImageVm = { id: number; url: string } | null;

/** CategoryGetVm (YAS): list item; parentId -1 = top level. */
export type Category = {
  id: number;
  name: string;
  slug: string;
  parentId: number;
  categoryImage: ImageVm;
};

/** CategoryGetDetailVm (YAS): parentId 0 = top level. */
export type CategoryDetail = {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  parentId: number;
  metaKeywords: string | null;
  metaDescription: string | null;
  displayOrder: number | null;
  isPublish: boolean | null;
  categoryImage: ImageVm;
};

/** CategoryPostVm (YAS): create and update body. */
export type CategoryPost = {
  name: string;
  slug: string;
  description: string | null;
  parentId: number | null;
  metaKeywords: string | null;
  metaDescription: string | null;
  displayOrder: number | null;
  isPublish: boolean;
  imageId: number | null;
};
