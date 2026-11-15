import Router from 'next/router';
import { FormEvent, KeyboardEvent, useEffect, useId, useRef, useState } from 'react';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import ListGroup from 'react-bootstrap/ListGroup';
import { getSuggestions } from '../services/SearchService';

const DEBOUNCE_MS = 250;
const MIN_CHARS = 2;
const MAX_KEYWORD = 100;

/**
 * Header search box: suggestions from the search service while typing (debounced, superseded requests aborted),
 * keyboard navigation (up/down/enter/escape), submit → `/search?keyword=`. Suggestions only carry a product name
 * (YAS ProductNameListVm), so choosing one runs the search for that name. If suggestions fail, the box still
 * searches.
 */
export default function SearchBox() {
  const listId = useId();
  const [value, setValue] = useState('');
  // what the user typed; choosing a suggestion changes `value` only, so it does not ask for new suggestions
  const [typed, setTyped] = useState('');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const inFlight = useRef<AbortController | null>(null);

  useEffect(() => {
    const term = typed.trim();
    inFlight.current?.abort();
    inFlight.current = null;
    if (term.length < MIN_CHARS) {
      return;
    }
    const timer = setTimeout(() => {
      const controller = new AbortController();
      inFlight.current = controller;
      getSuggestions(term, controller.signal)
        .then((result) => {
          if (!controller.signal.aborted) {
            setSuggestions(result.productNames.map((product) => product.name));
            setActive(-1);
          }
        })
        .catch(() => {
          if (!controller.signal.aborted) {
            setSuggestions([]);
          }
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [typed]);

  useEffect(() => () => inFlight.current?.abort(), []);

  const search = (keyword: string) => {
    const trimmed = keyword.trim();
    inFlight.current?.abort();
    setOpen(false);
    setActive(-1);
    Router.push({ pathname: '/search', query: trimmed ? { keyword: trimmed } : {} });
  };

  const choose = (name: string) => {
    setValue(name);
    search(name);
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    search(value);
  };

  const showList = open && suggestions.length > 0;

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActive((index) => Math.min(index + 1, suggestions.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((index) => Math.max(index - 1, -1));
    } else if (event.key === 'Escape') {
      setOpen(false);
      setActive(-1);
    } else if (event.key === 'Enter' && showList && active >= 0) {
      event.preventDefault();
      choose(suggestions[active]);
    }
  };

  const optionId = (index: number) => `${listId}-option-${index}`;

  return (
    <Form role="search" onSubmit={onSubmit} className="d-flex position-relative me-md-3 my-2 my-md-0">
      <Form.Control
        type="search"
        role="combobox"
        aria-label="Search products"
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={listId}
        aria-activedescendant={showList && active >= 0 ? optionId(active) : undefined}
        placeholder="Search products"
        autoComplete="off"
        maxLength={MAX_KEYWORD}
        size="sm"
        value={value}
        onChange={(event) => {
          setValue(event.target.value);
          setTyped(event.target.value);
          setOpen(true);
          if (event.target.value.trim().length < MIN_CHARS) {
            setSuggestions([]);
            setActive(-1);
          }
        }}
        onKeyDown={onKeyDown}
        onBlur={() => setOpen(false)}
      />
      <Button type="submit" variant="outline-light" size="sm" className="ms-2">
        Search
      </Button>
      {showList && (
        <ListGroup
          id={listId}
          role="listbox"
          className="position-absolute top-100 start-0 w-100 mt-1 shadow"
          style={{ zIndex: 1050 }}
        >
          {suggestions.map((name, index) => (
            <ListGroup.Item
              key={`${name}-${index}`}
              id={optionId(index)}
              role="option"
              aria-selected={index === active}
              active={index === active}
              action
              as="div"
              className="small"
              // mousedown would blur the input (closing the list) before the click lands
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(name)}
            >
              {name}
            </ListGroup.Item>
          ))}
        </ListGroup>
      )}
    </Form>
  );
}
