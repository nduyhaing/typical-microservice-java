import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import InputGroup from 'react-bootstrap/InputGroup';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { ProductOption } from '../models/Product';
import { createProductOption } from '../services/ProductOptionService';
import { generateVariations, parseValues, VariationRow } from '../utils/variations';

/** An option chosen for the product with the admin's comma-separated values. */
export type OptionInput = { id: number; name: string; valuesText: string };

/** A variation row being edited; the price stays text while typing. */
export type VariationInput = Omit<VariationRow, 'price'> & { price: string };

type Props = {
  /** All options known to the service (the select's choices). */
  options: ProductOption[];
  onOptionCreated: (option: ProductOption) => void;
  selected: OptionInput[];
  onSelectedChange: (selected: OptionInput[]) => void;
  variations: VariationInput[];
  onVariationsChange: (variations: VariationInput[]) => void;
  /** Product values the generated variations start from. */
  product: { name: string; sku: string; price: number };
};

export const toVariationInput = (row: VariationRow): VariationInput => ({ ...row, price: String(row.price) });

/**
 * Options + variations editor (YAS ProductVariation): choose options (or create one), enter their values, generate a
 * variation per combination, then adjust each variation's name, slug, SKU and price.
 */
export default function ProductVariations(props: Props) {
  const { options, selected, variations, onSelectedChange, onVariationsChange, product } = props;
  const [choice, setChoice] = useState('');
  const [newOption, setNewOption] = useState('');
  const [warning, setWarning] = useState<string>();
  const [error, setError] = useState<unknown>();

  const isSelected = (id: number) => selected.some((o) => o.id === id);

  function select(option: ProductOption) {
    if (!isSelected(option.id)) {
      onSelectedChange([...selected, { id: option.id, name: option.name, valuesText: '' }]);
    }
  }

  function addChosen() {
    const option = options.find((o) => String(o.id) === choice);
    if (!option) {
      setWarning('Choose an option first.');
      return;
    }
    setWarning(undefined);
    select(option);
    setChoice('');
  }

  async function create() {
    const name = newOption.trim();
    if (!name) {
      return;
    }
    setError(undefined);
    try {
      const option = await createProductOption(name);
      props.onOptionCreated(option);
      select(option);
      setNewOption('');
    } catch (e) {
      setError(e);
    }
  }

  function remove(option: OptionInput) {
    onSelectedChange(selected.filter((o) => o.id !== option.id));
    onVariationsChange(variations.filter((v) => !(option.id in v.optionValuesByOptionId)));
  }

  function generate() {
    const chosen = selected.map((o) => ({ id: o.id, name: o.name, values: parseValues(o.valuesText) }));
    if (chosen.length === 0 || chosen.some((o) => o.values.length === 0)) {
      setWarning('Enter at least one value for every option.');
      return;
    }
    setWarning(undefined);
    const existing = variations.map((v) => ({ ...v, price: Number(v.price) || 0 }));
    const all = generateVariations(product.name, product.sku, product.price, chosen, existing);
    onVariationsChange([...variations, ...all.slice(existing.length).map(toVariationInput)]);
  }

  const update = (index: number, patch: Partial<VariationInput>) =>
    onVariationsChange(variations.map((v, i) => (i === index ? { ...v, ...patch } : v)));

  const optionName = (id: string) => options.find((o) => String(o.id) === id)?.name ?? `Option ${id}`;

  return (
    <fieldset className="border rounded p-3 mb-3">
      <legend className="h6 float-none w-auto px-2">Options and variations</legend>
      {warning && <Alert variant="warning">{warning}</Alert>}
      {error !== undefined && <ErrorMessage error={error} />}
      <div className="row g-2 mb-3">
        <Form.Group className="col-md-6" controlId="variation-option">
          <Form.Label>Option</Form.Label>
          <InputGroup>
            <Form.Select value={choice} onChange={(e) => setChoice(e.target.value)}>
              <option value="">— choose —</option>
              {options.map((o) => (
                <option key={o.id} value={String(o.id)} disabled={isSelected(o.id)}>
                  {o.name}
                </option>
              ))}
            </Form.Select>
            <Button variant="outline-success" onClick={addChosen}>
              Add option
            </Button>
          </InputGroup>
        </Form.Group>
        <Form.Group className="col-md-6" controlId="variation-new-option">
          <Form.Label>New option</Form.Label>
          <InputGroup>
            <Form.Control value={newOption} maxLength={255} onChange={(e) => setNewOption(e.target.value)} />
            <Button variant="outline-success" onClick={create}>
              Create option
            </Button>
          </InputGroup>
        </Form.Group>
      </div>

      {selected.map((option) => (
        <Form.Group className="mb-2" controlId={`option-${option.id}-values`} key={option.id}>
          <Form.Label>{option.name} values</Form.Label>
          <InputGroup>
            <Form.Control
              placeholder="Comma separated, e.g. Red, Blue"
              value={option.valuesText}
              onChange={(e) =>
                onSelectedChange(selected.map((o) => (o.id === option.id ? { ...o, valuesText: e.target.value } : o)))
              }
            />
            <Button variant="outline-danger" onClick={() => remove(option)}>
              Remove {option.name}
            </Button>
          </InputGroup>
        </Form.Group>
      ))}
      {selected.length > 0 && (
        <Button variant="info" className="my-2" onClick={generate}>
          Generate variations
        </Button>
      )}

      {variations.length > 0 && (
        <Table size="sm" responsive className="mt-3">
          <thead>
            <tr>
              <th>Options</th>
              <th>Name</th>
              <th>Slug</th>
              <th>SKU</th>
              <th>Price</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {variations.map((variation, index) => {
              const label = `Variation ${index + 1}`;
              return (
                <tr key={JSON.stringify(variation.optionValuesByOptionId)}>
                  <td className="small text-nowrap">
                    {Object.entries(variation.optionValuesByOptionId)
                      .map(([id, value]) => `${optionName(id)}: ${value}`)
                      .join(', ')}
                  </td>
                  <td>
                    <Form.Control
                      size="sm"
                      aria-label={`${label} name`}
                      value={variation.name}
                      onChange={(e) => update(index, { name: e.target.value })}
                    />
                  </td>
                  <td>
                    <Form.Control
                      size="sm"
                      aria-label={`${label} slug`}
                      value={variation.slug}
                      onChange={(e) => update(index, { slug: e.target.value })}
                    />
                  </td>
                  <td>
                    <Form.Control
                      size="sm"
                      aria-label={`${label} SKU`}
                      value={variation.sku}
                      onChange={(e) => update(index, { sku: e.target.value })}
                    />
                  </td>
                  <td>
                    <Form.Control
                      size="sm"
                      type="number"
                      min={0}
                      step="0.01"
                      aria-label={`${label} price`}
                      value={variation.price}
                      onChange={(e) => update(index, { price: e.target.value })}
                    />
                  </td>
                  <td>
                    <Button
                      size="sm"
                      variant="outline-danger"
                      aria-label={`Remove variation ${index + 1}`}
                      onClick={() => onVariationsChange(variations.filter((_, i) => i !== index))}
                    >
                      Remove
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </Table>
      )}
    </fieldset>
  );
}
