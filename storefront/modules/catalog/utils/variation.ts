import { ProductOptionValueGet } from '../models/ProductOptionValueGet';
import { ProductOptions } from '../models/ProductOptions';
import { ProductVariation } from '../models/ProductVariation';

/** The variation whose every option equals the selected value; undefined if none (or nothing selected). */
export function findVariation(
  variations: ProductVariation[],
  selected: Record<number, string>
): ProductVariation | undefined {
  if (Object.keys(selected).length === 0) {
    return undefined;
  }
  return variations.find((variation) => {
    const entries = Object.entries(variation.options);
    return entries.length > 0 && entries.every(([optionId, value]) => selected[Number(optionId)] === value);
  });
}

/** Groups flat option/value pairs into one entry per option, as YAS's product page does. */
export function groupOptionValues(values: ProductOptionValueGet[]): ProductOptions[] {
  const options: ProductOptions[] = [];
  for (const value of values) {
    const existing = options.find((option) => option.id === value.productOptionId);
    if (existing) {
      existing.value.push(value.productOptionValue);
    } else {
      options.push({ id: value.productOptionId, name: value.productOptionName, value: [value.productOptionValue] });
    }
  }
  return options;
}
