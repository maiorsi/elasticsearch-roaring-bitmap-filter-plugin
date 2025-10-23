# elasticsearch-roaring-bitmap-filter-plugin

A custom Elasticsearch plugin that uses RoaringBitmap to provide very fast
filtering operations over document attributes (for example: security attributes,
access-control sets, feature flags, or any other set-like metadata stored per
document). The plugin exposes a small script engine that performs set
operations (contains, contained-by, overlap, equality) between an indexed
document's bitmap and a reference bitmap supplied at query time.

Goals
 - Fast membership and set operations using RoaringBitmap binary representations
 - Low-overhead script integration with Elasticsearch search-time filtering
 - Simple packaging so the plugin can be installed into an ES 9.1.1 node

Key files and layout
 - `lib/src/main/java/io/github/maiorsi/elasticsearch/RoaringBitmapFilterPlugin.java`
	 — main plugin and script engine implementation.
 - `lib/src/assembly/plugin-descriptor.properties` — plugin descriptor (classname,
	 ES/java version, name, version) included in the distribution zip.
 - `lib/build.gradle.kts` — module build configuration producing the plugin zip
	 (task: `:lib:distZip` -> `lib/build/distributions/es-rb-plugin.zip`).
 - `docker/Dockerfile` and `docker-compose.yml` — sample Docker setup to run
	 Elasticsearch 9.1.1 with the built plugin installed.
 - `build.gradle.kts` (root) — convenience Gradle task `dockerComposeUp` which
 	depends on `:lib:distZip`.

Features / supported operations
 - CONTAINS (aliases: `@>`, `contains`) — document bitmap contains reference
 - IS_CONTAINED_BY (aliases: `<@`, `is_contained_by`) — document bitmap is
	 contained by reference
 - OVERLAP (aliases: `&&`, `overlap`) — bitmaps intersect
 - EQUAL (aliases: `=`, `equal`) — bitmaps are equal
 - NOT_EQUAL (aliases: `<>`, `not_equal`) — bitmaps differ

How the script is called
 - Script engine type: `roaring_bitmap`
 - Script source/name expected by the engine: `roaring_bitmap_filter`

This means you can use a script in a query like:

```json
{
	"query": {
		"bool": {
			"filter": [
				{
					"script": {
						"script": {
							"lang": "roaring_bitmap",
							"source": "roaring_bitmap_filter",
							"params": {
								"field": "security_attrs",
								"terms": "<BASE64_ROARING_BITMAP>",
								"operation": "@>"
							}
						}
					}
				}
			]
		}
	}
}
```

Parameters
 - `field` (string) — the binary doc-values field name containing serialized
	 RoaringBitmap bytes for each document (the code expects binary doc values).
 - `terms` (string) — base64-encoded serialized RoaringBitmap used as the
	 reference bitmap for the operation.
 - `operation` (string) — one of the supported operators/symbols or textual
	 aliases (see supported operations above).

How to build (local)
1. Build the module and create the plugin zip (produces `lib/build/distributions/es-rb-plugin.zip`):

```fish
./gradlew :lib:build :lib:distZip
```

2. Verify the produced zip contains the plugin class by inspecting it manually:

```fish
unzip -l lib/build/distributions/es-rb-plugin.zip
unzip -d /tmp/es-rb-plugin lib/build/distributions/es-rb-plugin.zip
find /tmp/es-rb-plugin -type f -name "*.jar" -exec jar tf {} \; | grep RoaringBitmapFilterPlugin.class
```

Run with Docker Compose
 - The repository includes a `docker/Dockerfile` and a `docker-compose.yml`.
	 The Dockerfile installs the generated plugin zip into an Elasticsearch
	 9.1.1 image.

Simplest (rebuild and run):

```fish
# build the zip and start ES (this root task depends on :lib:distZip and verify)
./gradlew dockerComposeUp
```

Notes
 - The Dockerfile expects `lib/build/distributions/es-rb-plugin.zip` to exist
	 when the image builds. If you prefer not to rebuild the image on every
	 change, we can switch to installing the plugin at container runtime from a
	 mounted volume instead.

Building RoaringBitmaps for use as `terms`
 - The `terms` parameter must be a base64 string of a serialized RoaringBitmap.
	 Example Java snippet to create and serialize a bitmap (for preparing test
	 data outside ES):

```java
RoaringBitmap rb = new RoaringBitmap();
rb.add(1);
rb.add(42);
ByteArrayOutputStream baos = new ByteArrayOutputStream();
rb.runOptimize();
rb.serialize(new DataOutputStream(baos));
String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
System.out.println(b64);
```

Development tips
 - Source is placed in `lib/src/main/java/...`. If you rename packages, ensure
	 the `plugin-descriptor.properties` `classname` entry is updated to match
	 the new FQN and that the `distZip` includes the jar containing the class.
 - The root Gradle task `dockerComposeUp` depends on `:lib:distZip` so it will
 	build the plugin before running Docker Compose; if packaging is incorrect
 	you'll need to rebuild the plugin zip and inspect it manually.

Acknowledgements
 - This project was inspired by and builds on ideas from `fastfilter-elasticsearch-plugin` by Luís Sena.
	 See: https://github.com/lsena/fastfilter-elasticsearch-plugin

License
 - See the repository `LICENSE` included in this project.

Contact / contributions
 - Pull requests and issues are welcome. For faster iteration when working on
	 the plugin, prefer running ES locally via the provided Docker Compose setup
	 and reusing the `:lib:distZip` -> `docker compose up --build` workflow.


