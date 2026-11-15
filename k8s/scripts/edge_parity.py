"""Edge parity between compose nginx and the Kubernetes ingress (decisions.md 6-60).

Usage: edge_parity.py <nginx default.conf.template> <rendered umbrella chart>

Emulates request routing on both edges for a list of probe paths and compares the outcome (an HTTP status the edge
answers itself, or the upstream service the request is proxied to):

* compose: nginx location matching of the api/storefront/backoffice server blocks (exact, longest prefix, ^~,
  regexes in order) as in http://nginx.org/en/docs/http/ngx_http_core_module.html#location;
* Kubernetes: the Ingress rules of the rendered chart (pathType Prefix, longest path wins, element-wise match) and,
  for paths routed to the edge-deny nginx, the same location matching over its ConfigMap.

The probes cover every service either edge knows (compose `location /<svc>/` and the chart's api paths): its
internal, actuator and health paths, plus the endpoint-specific blocks. Also checks that compose's browser-only
hosts (storefront, backoffice, identity) drop browser-supplied trace headers (traceparent, tracestate, baggage).

Only canonical lower-case paths are probed: compose's case-insensitive, multi-slash regexes are a second line of
defence that plain Prefix paths cannot express; the services' own routing is case-sensitive (decisions.md 6-60).
Standard library only (runs on any CI runner).
"""

import re
import sys

TRACE_HEADERS = ("traceparent", "tracestate", "baggage")


def blocks(text, keyword):
    """Yields (header, body) of every `keyword ... { body }` block at any depth (braces balanced)."""
    for match in re.finditer(r"(?m)^\s*" + keyword + r"\b([^{;]*)\{", text):
        depth, start = 1, match.end()
        i = start
        while depth:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
            i += 1
        yield match.group(1).strip(), text[start:i - 1]


def outcome(body):
    returned = re.search(r"\breturn\s+(\d{3})", body)
    if returned:
        return returned.group(1)
    upstream = re.search(r"\bset\s+\$\w+\s+([\w.-]+):\d+;", body)
    if upstream and "proxy_pass" in body:
        return "proxy:" + upstream.group(1)
    raise ValueError("location without return or proxy_pass: " + body.strip()[:80])


def locations(server_body):
    result = []
    for header, body in blocks(server_body, "location"):
        parts = header.split(None, 1)
        if parts[0] in ("=", "~", "~*", "^~"):
            modifier, pattern = parts[0], parts[1].strip()
        else:
            modifier, pattern = "", header
        result.append((modifier, pattern, outcome(body), body))
    return result


def nginx_route(locs, path):
    for modifier, pattern, result, _ in locs:
        if modifier == "=" and path == pattern:
            return result
    prefixes = [(p, m, r) for m, p, r, _ in locs if m in ("", "^~") and path.startswith(p)]
    longest = max(prefixes, key=lambda item: len(item[0])) if prefixes else None
    if longest and longest[1] == "^~":
        return longest[2]
    for modifier, pattern, result, _ in locs:
        if modifier in ("~", "~*") and re.search(pattern, path, re.I if modifier == "~*" else 0):
            return result
    if longest:
        return longest[2]
    return "no location"


def compose_servers(template):
    servers = {}
    for _, body in blocks(template, "server"):
        name = re.search(r"server_name\s+([\w-]+)\.\$\{DOMAIN\};", body)
        if name:
            servers[name.group(1)] = body
    return servers


def ingress_rules(rendered):
    """{host prefix: [(path, service, port)]} from the rendered Ingress documents."""
    rules = {}
    for doc in rendered.split("\n---"):
        if not re.search(r"(?m)^kind: Ingress$", doc):
            continue
        host = None
        for line_match in re.finditer(
                r"(?m)^\s*- host: (\S+)$|^\s*- path: (\S+)\n(?:.*\n)*?\s*name: (\S+)\n\s*port:\n\s*number: (\d+)",
                doc):
            if line_match.group(1):
                host = line_match.group(1).split(".")[0]
                rules.setdefault(host, [])
            else:
                rules[host].append((line_match.group(2), line_match.group(3), int(line_match.group(4))))
    return rules


def edge_deny_conf(rendered):
    match = re.search(r"(?ms)^kind: ConfigMap\nmetadata:\n  name: edge-deny\n.*?default\.conf: \|\n(.*?)(?=^---|\Z)",
                      rendered)
    if not match:
        raise ValueError("edge-deny ConfigMap not found in the rendered chart")
    return "\n".join(line[4:] for line in match.group(1).splitlines())


def k8s_route(paths, deny_locs, path):
    candidates = [p for p in paths
                  if p[0] == "/" or path == p[0] or path.startswith(p[0].rstrip("/") + "/")]
    if not candidates:
        return "no ingress path"
    path_rule = max(candidates, key=lambda p: len(p[0]))
    if path_rule[1] == "edge-deny":
        return nginx_route(deny_locs, path)
    return "proxy:" + path_rule[1]


def probes_for_api(services):
    paths = ["/", "/unknown", "/unknown/x",
             "/product/backoffice/products/subtract-quantity", "/product/backoffice/products/restore-quantity",
             "/product/backoffice/products/subtract-quantity/1",
             "/promotion/storefront/promotions/apply-usage", "/promotion/storefront/promotions/release-usage"]
    for svc in sorted(services):
        for suffix in ("/", "/x", "/backoffice/x", "/internal", "/internal/", "/internal/x/y", "/actuator",
                       "/actuator/", "/actuator/metrics", "/actuator/env", "/actuator/health",
                       "/actuator/health/liveness"):
            paths.append("/" + svc + suffix)
    return paths


BFF_PROBES = ["/", "/x", "/api/product/storefront/x", "/actuator", "/actuator/", "/actuator/env",
              "/actuator/health", "/actuator/health/readiness"]


def main(template_file, rendered_file):
    template = open(template_file, encoding="utf-8").read()
    rendered = open(rendered_file, encoding="utf-8").read()
    servers = compose_servers(template)
    rules = ingress_rules(rendered)
    deny_locs = locations(next(body for _, body in blocks(edge_deny_conf(rendered), "server")))
    failures = []

    api_compose = locations(servers["api"])
    services = {re.match(r"/([\w-]+)/", p).group(1) for m, p, _, _ in api_compose if m == "" and p != "/"}
    services |= {p.strip("/").split("/")[0] for p, _, _ in rules.get("api", []) if p != "/"}
    hosts = [("api", probes_for_api(services))]
    hosts += [(host, BFF_PROBES) for host in ("storefront", "backoffice")]
    count = 0
    for host, probes in hosts:
        compose_locs = locations(servers[host])
        for path in probes:
            count += 1
            want = nginx_route(compose_locs, path)
            got = k8s_route(rules.get(host, []), deny_locs, path)
            if want != got:
                failures.append(f"{host}{path}: compose {want}, k8s {got}")

    # browser-supplied trace context never reaches a service through the browser-only hosts (decisions.md 6-62).
    # Not on the api host: the BFFs and services call it themselves and must propagate their own trace context;
    # not in k8s: ingress-nginx's proxy-set-headers is global, so it would also cut those in-cluster calls.
    for host in ("storefront", "backoffice", "identity"):
        for modifier, pattern, result, body in locations(servers[host]):
            if result.startswith("proxy:"):
                for header in TRACE_HEADERS:
                    if not re.search(r'proxy_set_header\s+' + header + r'\s+"";', body):
                        failures.append(f"compose {host} location {pattern}: no proxy_set_header {header} \"\"")

    for failure in failures:
        print("MISMATCH " + failure)
    if failures:
        print(f"Edge parity: {len(failures)} mismatch(es) in {count} probes.")
        return 1
    print(f"Edge parity: {count} probes, compose nginx and the k8s ingress agree; browser hosts drop trace headers.")
    return 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
