import { slugify } from '../../../common/utils/slug';
import {
  ProductOption,
  ProductOptionValueDisplay,
  ProductOptionValuePost,
  ProductVariationGet,
  ProductVariationPost,
} from '../models/Product';

/** An option chosen for a product, with the values the admin entered for it. */
export type SelectedOption = { id: number; name: string; values: string[] };

/** One variation row of the form: ProductVariationPutVm (`id` null for a new variation). */
export type VariationRow = ProductVariationPost & { id: number | null };

/** YAS displays every value as text with a default colour; the form does not edit display styles. */
const DISPLAY_TYPE = 'text';
const DEFAULT_COLOUR = '#000000';

/** "Red, Blue" -> ['Red', 'Blue'] (trimmed, no empties, no duplicates). */
export function parseValues(text: string): string[] {
  return [...new Set(text.split(',').map((v) => v.trim()).filter(Boolean))];
}

/** Every combination of the options' values (YAS `generateDistinctProductOptionCombinations`). */
export function combineOptionValues(options: SelectedOption[]): Record<number, string>[] {
  if (options.length === 0 || options.some((o) => o.values.length === 0)) {
    return [];
  }
  return options.reduce<Record<number, string>[]>(
    (combinations, option) => combinations.flatMap((c) => option.values.map((value) => ({ ...c, [option.id]: value }))),
    [{}]
  );
}

const combinationKey = (values: Record<number, string>) =>
  JSON.stringify(
    Object.entries(values)
      .map(([id, value]) => [Number(id), value] as const)
      .sort(([a], [b]) => a - b)
  );

/**
 * Existing variations plus one per missing combination, named "<product> <values...>" like YAS. The SKU gets the
 * values as suffix so variations do not share the product's SKU (the service refuses duplicate SKUs).
 */
export function generateVariations(
  productName: string,
  sku: string,
  price: number,
  options: SelectedOption[],
  existing: VariationRow[]
): VariationRow[] {
  const known = new Set(existing.map((v) => combinationKey(v.optionValuesByOptionId)));
  const added = combineOptionValues(options)
    .filter((combination) => !known.has(combinationKey(combination)))
    .map((combination): VariationRow => {
      const values = options.map((o) => combination[o.id]);
      const name = [productName.trim(), ...values].join(' ').trim();
      const valuesSlug = slugify(values.join(' '));
      return {
        id: null,
        name,
        slug: slugify(name),
        sku: sku.trim() ? `${sku.trim()}-${valuesSlug}` : slugify(name),
        gtin: '',
        price,
        thumbnailMediaId: null,
        productImageIds: [],
        optionValuesByOptionId: combination,
      };
    });
  return [...existing, ...added];
}

/** Per option, the values the variations use (YAS `createProductOptionValues`), in order of appearance. */
export function toProductOptionValues(variations: Pick<VariationRow, 'optionValuesByOptionId'>[]): ProductOptionValuePost[] {
  const byOption = new Map<number, string[]>();
  variations.forEach((variation) =>
    Object.entries(variation.optionValuesByOptionId).forEach(([id, value]) => {
      const values = byOption.get(Number(id)) ?? [];
      if (!values.includes(value)) {
        values.push(value);
      }
      byOption.set(Number(id), values);
    })
  );
  return [...byOption.entries()].map(([productOptionId, value], index) => ({
    productOptionId,
    displayType: DISPLAY_TYPE,
    displayOrder: index + 1,
    value,
  }));
}

/** YAS `productOptionValueDisplays`: the values as a JSON object of value -> display colour. */
export function toProductOptionValueDisplays(optionValues: ProductOptionValuePost[]): ProductOptionValueDisplay[] {
  return optionValues.map((o) => ({
    productOptionId: o.productOptionId,
    displayType: o.displayType,
    displayOrder: o.displayOrder,
    value: JSON.stringify(Object.fromEntries(o.value.map((v) => [v, DEFAULT_COLOUR]))),
  }));
}

/** The options (and their values) an existing product uses, read from its variations. */
export function optionsFromVariations(variations: ProductVariationGet[], allOptions: ProductOption[]): SelectedOption[] {
  return toProductOptionValues(variations.map((v) => ({ optionValuesByOptionId: v.options ?? {} }))).map((o) => ({
    id: o.productOptionId,
    name: allOptions.find((option) => option.id === o.productOptionId)?.name ?? `Option ${o.productOptionId}`,
    values: o.value,
  }));
}

/** An existing variation as a form row (ProductVariationPutVm). */
export function toVariationRow(variation: ProductVariationGet): VariationRow {
  return {
    id: variation.id,
    name: variation.name,
    slug: variation.slug,
    sku: variation.sku ?? '',
    gtin: variation.gtin ?? '',
    price: variation.price ?? 0,
    thumbnailMediaId: variation.thumbnail?.id ?? null,
    productImageIds: (variation.productImages ?? []).map((image) => image.id),
    optionValuesByOptionId: Object.fromEntries(Object.entries(variation.options ?? {}).map(([id, v]) => [Number(id), v])),
  };
}

/** A price as typed: required, a number, 0 or more (a blank price must never become 0, i.e. free). */
export function isValidPrice(text: string): boolean {
  if (!text.trim()) {
    return false;
  }
  const price = Number(text);
  return Number.isFinite(price) && price >= 0;
}
