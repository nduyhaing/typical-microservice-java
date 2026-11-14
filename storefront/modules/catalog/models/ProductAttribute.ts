export type ProductAttribute = {
  name: string;
  value: string;
};

export type ProductAttributeGroup = {
  name: string;
  productAttributeValues: ProductAttribute[];
};
