# search-roaring-bitmap-filter-plugin

A custom search (Elasticsearch and Opensearch) plugin that uses RoaringBitmap to provide very fast
filtering operations over document attributes (for example: security attributes,
access-control sets, feature flags, or any other set-like metadata stored per
document). The plugin exposes a small script engine that performs set
operations (contains, contained-by, overlap, equality) between an indexed
document's bitmap and a reference bitmap supplied at query time.

Goals
 - Fast membership and set operations using RoaringBitmap binary representations
 - Low-overhead script integration with search-time filtering
 - Simple packaging so the plugin can be installed into an ES 9.2.0 node or OS 2.4.0 node

Key files and layout
 - `es/src/main/java/io/github/maiorsi/elasticsearch/RoaringBitmapFilterPlugin.java`
 	 — Elasticsearch plugin implementation and script engine.
 - `es/src/assembly/plugin-descriptor.properties` — ES plugin descriptor (classname,
 	 ES/java version, name, version) included in the distribution zip.
 - `es/build.gradle.kts` — ES module build config (produces `es/build/distributions/es-rb-plugin.zip` via `:es:distZip`).
 - `os/src/main/java/io/github/maiorsi/opensearch/RoaringBitmapFilterPlugin.java` — OpenSearch variant of the same plugin.
 - `os/src/assembly/plugin-descriptor.properties` — OS plugin descriptor (opensearch/java version, name, version).
 - `os/build.gradle.kts` — OS module build config (produces `os/build/distributions/os-rb-plugin.zip` via `:os:distZip`).
 - `docker/Dockerfile` and `docker-compose.yml` — sample Docker setup to run
	 Elasticsearch 9.2.0 with the built plugin installed.
 - `build.gradle.kts` (root) — convenience Gradle task `dockerComposeUp` which
 	depends on `:es:distZip`.

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

You can build either variant independently.

1. Build the Elasticsearch plugin and create its plugin zip:

```fish
./gradlew :es:build :es:distZip
```

2. Build the OpenSearch plugin and create its plugin zip:

```fish
./gradlew :os:build :os:distZip
```

3. Verify a produced zip contains the plugin class by inspecting it manually (example for ES):

```fish
unzip -l es/build/distributions/es-rb-plugin.zip
unzip -d /tmp/es-rb-plugin es/build/distributions/es-rb-plugin.zip
find /tmp/es-rb-plugin -type f -name "*.jar" -exec jar tf {} \; | grep RoaringBitmapFilterPlugin.class
```

Run with Docker Compose
 - The repository includes a `docker/es.dockerfile` for Elasticsearch and a `docker/os.dockerfile` for Opensearch and a `docker-compose.yml`.
 	The Dockerfile installs the generated plugin zips into an Elasticsearch 9.2.0 image and Opensearch 2.4.0 image respectively.

Simplest (rebuild and run):

```fish
# build the zip and start ES (this root task depends on :es:distZip)
./gradlew dockerComposeUp
```

Notes
 - The Dockerfile expects `es/build/distributions/es-rb-plugin.zip` to exist
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
 - Source is placed in `es/src/main/java/...`. If you rename packages, ensure
 	the `plugin-descriptor.properties` `classname` entry is updated to match
	 the new FQN and that the `distZip` includes the jar containing the class.
 - The root Gradle task `dockerComposeUp` depends on `:es:distZip` so it will
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
	 and reusing the `:es:distZip` -> `docker compose up --build` workflow.


