# Enrichment strategy:
# The enriched file always stores a hash (x-raw-hash) for each schema/operation (parts),
# calculated from schema/operation (part) of the original raw JSON file (created by Springdoc based on current code).
# On each run, the new raw JSON is regenerated from code and each part's hash is compared (previous vs new raw json)
#   - unchanged → skip entirely, no API call
#   - changed   → re-enrich:
#                   Claude fills only MISSING fields (description, summary, example);
#                   existing text always wins — prompt forbids removing or overwriting any field.
# This means code annotations (@Schema, @Operation) are the primary source of truth;
# Claude only fills the gaps the developer left blank.

import hashlib
import json
import os
import time
from copy import deepcopy
from pathlib import Path

from anthropic import Anthropic

RAW_OPENAPI_PATH = "openapi.json"
ENRICHED_OPENAPI_PATH = "src/main/resources/static/openapi/enhanced-openapi.json"

RELEVANT_PATTERNS = [
    "Controller.java",
    "Request.java",
    "Response.java",
    "ErrorResponse.java",
]

MAX_FILE_CHARS = 3000
MAX_TOKENS = 4096
MAX_RETRIES = 3

client = Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def compute_hash(obj):
    return hashlib.md5(json.dumps(obj, sort_keys=True).encode()).hexdigest()


def is_ref(schema):
    return isinstance(schema, dict) and "$ref" in schema


def build_prompt(section_name, section_data, repository_context):
    return f"""You are enriching an existing OpenAPI object for Swagger UI documentation.

Your task is STRICTLY LIMITED to adding missing:
- description
- summary
- example

CRITICAL RULES:
- NEVER remove existing fields
- NEVER rename, reorder, or rewrite the existing schema structure
- NEVER modify: types, formats, enums, validations, required fields, operationIds, tags, paths, $ref values
- Replace generic HTTP status text ("ok", "not found", "Bad Request", "Created") with meaningful domain-specific descriptions
- For response content schemas, add realistic example values that reflect actual domain data
- Return the SAME JSON object structure you received
- Return ONLY valid JSON with no markdown fences

Repository code context:
{repository_context}

OpenAPI section: {section_name}

OpenAPI JSON:
{json.dumps(section_data, separators=(",", ":"))}
"""


def enrich_with_ai(section_name, section_data, repository_context):
    prompt = build_prompt(section_name, section_data, repository_context)

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            response = client.messages.create(
                model="claude-sonnet-4-6",
                max_tokens=MAX_TOKENS,
                temperature=0,
                messages=[{"role": "user", "content": prompt}],
            )

            text = response.content[0].text.strip()

            if text.startswith("```json"):
                text = text.replace("```json", "", 1).rsplit("```", 1)[0].strip()
            elif text.startswith("```"):
                text = text.replace("```", "", 1).rsplit("```", 1)[0].strip()

            result = json.loads(text)
            _validate_enrichment(section_data, result)
            return result

        except (json.JSONDecodeError, ValueError) as e:
            print(f"Attempt {attempt}/{MAX_RETRIES} failed for '{section_name}': {e}")
            if attempt == MAX_RETRIES:
                print(f"All retries exhausted, keeping original for: {section_name}")
                return section_data
            time.sleep(2 ** attempt)

    return section_data


def _validate_enrichment(original, enriched):
    """Raise if Claude silently dropped top-level keys from the original."""
    dropped = set(original.keys()) - set(enriched.keys())
    if dropped:
        raise ValueError(f"Claude dropped fields: {dropped}")


def schema_needs_enrichment(schema):
    """Recursively check if schema or any nested field is missing description/example."""
    if not isinstance(schema, dict) or is_ref(schema):
        return False

    if "description" not in schema:
        return True
    if "example" not in schema:
        return True

    for prop in schema.get("properties", {}).values():
        if is_ref(prop):
            continue
        if "description" not in prop or "example" not in prop:
            return True
        if schema_needs_enrichment(prop):
            return True

    items = schema.get("items")
    if items and not is_ref(items):
        if "description" not in items or "example" not in items:
            return True

    return False


def operation_needs_enrichment(operation):
    if "summary" not in operation:
        return True
    if "description" not in operation:
        return True

    for param in operation.get("parameters", []):
        if "description" not in param:
            return True
        schema = param.get("schema", {})
        if not is_ref(schema):
            if "description" not in schema or "example" not in schema:
                return True

    request_body = operation.get("requestBody", {})
    if request_body:
        if "description" not in request_body:
            return True
        for media_type in request_body.get("content", {}).values():
            schema = media_type.get("schema", {})
            if not is_ref(schema):
                if "description" not in schema or "example" not in schema:
                    return True

    for response in operation.get("responses", {}).values():
        if "description" not in response:
            return True
        for media_type in response.get("content", {}).values():
            schema = media_type.get("schema", {})
            if not is_ref(schema):
                if "description" not in schema or "example" not in schema:
                    return True

    return False


def is_relevant_file(file_path):
    return any(pattern in file_path for pattern in RELEVANT_PATTERNS)


def collect_schema_context(schema_name):
    """Find Java files whose path contains the schema name."""
    context_parts = []
    for path in Path("src/main/java").rglob("*.java"):
        file_path = str(path)
        if not is_relevant_file(file_path) or schema_name not in file_path:
            continue
        try:
            content = path.read_text(encoding="utf-8")
            context_parts.append(f"FILE: {file_path}\n{content[:MAX_FILE_CHARS]}")
            print(f"  Context: {file_path}")
        except Exception as e:
            print(f"  Failed reading {file_path}: {e}")
    return "\n".join(context_parts)


def collect_operation_context(operation):
    """Find controller and request/response files matching the operation's tags."""
    # Split each tag into words so "Public Buildings" matches "PublicBuildingController"
    # (joining strips spaces but keeps singular/plural independent: "Building" in path)
    tag_words = [
        word
        for tag in operation.get("tags", [])
        for word in tag.split()
        if len(word) > 3
    ]
    context_parts = []
    for path in Path("src/main/java").rglob("*.java"):
        file_path = str(path)
        if not is_relevant_file(file_path):
            continue
        if not any(word in file_path for word in tag_words):
            continue
        try:
            content = path.read_text(encoding="utf-8")
            context_parts.append(f"FILE: {file_path}\n{content[:MAX_FILE_CHARS]}")
            print(f"  Context: {file_path}")
        except Exception as e:
            print(f"  Failed reading {file_path}: {e}")
    return "\n".join(context_parts)


def main():
    raw_openapi = load_json(RAW_OPENAPI_PATH)

    try:
        enriched_openapi = load_json(ENRICHED_OPENAPI_PATH)
    except FileNotFoundError:
        enriched_openapi = deepcopy(raw_openapi)

    # Always sync top-level metadata from raw so version/servers/tags stay current
    for key in ("openapi", "info", "servers", "tags"):
        if key in raw_openapi:
            enriched_openapi[key] = raw_openapi[key]

    changed = False

    # ----------------------------------------------------------------
    # SCHEMA ENRICHMENT
    # ----------------------------------------------------------------
    raw_schemas = raw_openapi.get("components", {}).get("schemas", {})
    enriched_schemas = (
        enriched_openapi
        .setdefault("components", {})
        .setdefault("schemas", {})
    )

    for schema_name, raw_schema in raw_schemas.items():
        raw_hash = compute_hash(raw_schema)
        existing = enriched_schemas.get(schema_name, {})

        if existing.get("x-raw-hash") == raw_hash and not schema_needs_enrichment(existing):
            print(f"Schema unchanged, skipping: {schema_name}")
            continue

        print(f"Enriching schema: {schema_name}")
        context = collect_schema_context(schema_name)
        enriched = enrich_with_ai(schema_name, raw_schema, context)
        enriched["x-raw-hash"] = raw_hash
        enriched_schemas[schema_name] = enriched
        changed = True

    # ----------------------------------------------------------------
    # PATH / OPERATION ENRICHMENT
    # ----------------------------------------------------------------
    raw_paths = raw_openapi.get("paths", {})
    enriched_paths = enriched_openapi.setdefault("paths", {})

    for path, methods in raw_paths.items():
        enriched_paths.setdefault(path, {})

        for method, raw_operation in methods.items():
            raw_hash = compute_hash(raw_operation)
            existing = enriched_paths[path].get(method, {})

            if existing.get("x-raw-hash") == raw_hash and not operation_needs_enrichment(existing):
                print(f"Operation unchanged, skipping: {method.upper()} {path}")
                continue

            print(f"Enriching operation: {method.upper()} {path}")
            context = collect_operation_context(raw_operation)
            enriched = enrich_with_ai(f"{method.upper()} {path}", raw_operation, context)
            enriched["x-raw-hash"] = raw_hash
            enriched_paths[path][method] = enriched
            changed = True

    if changed:
        save_json(ENRICHED_OPENAPI_PATH, enriched_openapi)
        print("Enhanced OpenAPI updated successfully.")
    else:
        print("No changes detected, skipping file write.")


if __name__ == "__main__":
    main()
