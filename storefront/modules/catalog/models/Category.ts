export type Category = {
  id: number;
  name: string;
  slug: string;
  parentId: number;
  categoryImage?: { id: number; url: string } | null;
};
