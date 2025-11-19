package io.github.maiorsi.opensearch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.util.BytesRef;
import org.opensearch.ExceptionsHelper;
import org.opensearch.common.settings.Settings;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.ScriptPlugin;
import org.opensearch.script.*;
import org.opensearch.search.lookup.SearchLookup;
import org.roaringbitmap.RoaringBitmap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * OpenSearch variant of the Roaring Bitmap filter plugin.
 */
public class RoaringBitmapFilterPlugin extends Plugin implements ScriptPlugin {
    private static final short SERIAL_COOKIE_NO_RUNCONTAINER = 12346;
    private static final short SERIAL_COOKIE = 12347;
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final Logger logger = LogManager.getLogger(RoaringBitmapFilterPlugin.class);

    private enum Operation {
        CONTAINS("@>", "contains"),
        IS_CONTAINED_BY("<@", "is_contained_by"),
        OVERLAP("&&", "overlap"),
        EQUAL("=", "equal"),
        NOT_EQUAL("<>", "not_equal");

        private final String[] aliases;

        Operation(String... aliases) {
            this.aliases = aliases;
        }

        public static Operation fromString(String op) {
            return Arrays.stream(values())
                .filter(v -> Arrays.stream(v.aliases).anyMatch(alias -> alias.equals(op)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid operation: " + op));
        }
    }

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new RoaringBitmapFilterScriptEngine();
    }

    public static byte[] findDataWithMagicBytes(byte[] sourceArray) {
        if (sourceArray == null || sourceArray.length < 2) {
            return EMPTY_BYTE_ARRAY;
        }

        for (int i = 0; i < sourceArray.length - 1; i++) {
            short current = (short) ((sourceArray[i] & 0xFF) | (sourceArray[i + 1] << 8));

            if (current == SERIAL_COOKIE_NO_RUNCONTAINER || current == SERIAL_COOKIE) {
                int resultLength = sourceArray.length - i;
                byte[] result = new byte[resultLength];
                System.arraycopy(sourceArray, i, result, 0, resultLength);
                return result;
            }
        }

        return EMPTY_BYTE_ARRAY;
    }

    private static class RoaringBitmapFilterScriptEngine implements ScriptEngine {
        private static final String SCRIPT_TYPE = "roaring_bitmap";
        private static final String SCRIPT_NAME = "roaring_bitmap_filter";

        @Override
        public String getType() {
            return SCRIPT_TYPE;
        }

        @Override
        public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context,
                Map<String, String> params) {
            if (context != FilterScript.CONTEXT) {
                throw new IllegalArgumentException(
                        String.format("%s scripts cannot be used for context [%s]",
                                SCRIPT_TYPE, context.name));
            }

            if (!SCRIPT_NAME.equals(scriptSource)) {
                throw new IllegalArgumentException("Unknown script name: " + scriptSource);
            }

            return context.factoryClazz.cast(new RoaringBitmapFilterFactory());
        }

        @Override
        public void close() {
        }

        @Override
        public Set<ScriptContext<?>> getSupportedContexts() {
            return Set.of(FilterScript.CONTEXT);
        }
    }

    private static class RoaringBitmapFilterFactory implements FilterScript.Factory {
        private static final String FIELD_PARAM = "field";
        private static final String TERMS_PARAM = "terms";
        private static final String OPERATION_PARAM = "operation";

        @Override
        public FilterScript.LeafFactory newFactory(Map<String, Object> params, SearchLookup lookup) {
            validateParameters(params);

            String terms = params.get(TERMS_PARAM).toString();
            Operation operation = Operation.fromString(params.get(OPERATION_PARAM).toString());

            RoaringBitmap rBitmap = deserializeBitmap(terms);
            return new RoaringBitmapFilterLeafFactory(params, lookup, rBitmap, operation);
        }

        private void validateParameters(Map<String, Object> params) {
            Map.of(
                FIELD_PARAM, "Missing parameter [field]",
                TERMS_PARAM, "Missing parameter [terms]",
                OPERATION_PARAM, "Missing parameter [operation]").forEach((key, message) -> {
                    if (!params.containsKey(key)) {
                        throw new IllegalArgumentException(message);
                    }
                });
        }

        private RoaringBitmap deserializeBitmap(String terms) {
            try {
                RoaringBitmap rBitmap = new RoaringBitmap();
                rBitmap.deserialize(ByteBuffer.wrap(Base64.getDecoder().decode(terms)));
                return rBitmap;
            } catch (IOException e) {
                throw ExceptionsHelper.convertToOpenSearchException(e);
            }
        }

        @Override
        public boolean isResultDeterministic() {
            return true;
        }
    }

    private static class RoaringBitmapFilterLeafFactory implements FilterScript.LeafFactory {
        private final Map<String, Object> params;
        private final SearchLookup lookup;
        private final String field;
        private final RoaringBitmap rBitmap;
        private final Operation operation;

        RoaringBitmapFilterLeafFactory(Map<String, Object> params, SearchLookup lookup,
                RoaringBitmap rBitmap, Operation operation) {
            this.params = params;
            this.lookup = lookup;
            this.rBitmap = rBitmap;
            this.field = params.get("field").toString();
            this.operation = operation;
        }

        @Override
        public FilterScript newInstance(LeafReaderContext ctx) throws IOException {
            final BinaryDocValues leafDocValues = ctx.reader().getBinaryDocValues(field);
            if (leafDocValues == null) {
                return new FilterScript(params, lookup, ctx) {
                    @Override
                    public boolean execute() {
                        return false;
                    }
                };
            }

            return new FilterScript(params, lookup, ctx) {
                // per-leaf doc values reference
                private final BinaryDocValues docValues = leafDocValues;
                private boolean missing = false;

                @Override
                public void setDocument(int docid) {
                    try {
                        // advanceExact positions to the requested doc and returns whether a value exists
                        this.missing = !docValues.advanceExact(docid);
                    } catch (IOException e) {
                        throw ExceptionsHelper.convertToOpenSearchException(e);
                    }
                    super.setDocument(docid);
                }

                @Override
                public boolean execute() {
                    if (missing) {
                        return false;
                    }
                    try {
                        BytesRef docVal = docValues.binaryValue();
                        RoaringBitmap docBitmap = new RoaringBitmap();
                        docBitmap.deserialize(ByteBuffer.wrap(findDataWithMagicBytes(docVal.bytes)));

                        return switch (operation) {
                            case CONTAINS -> docBitmap.contains(rBitmap);
                            case IS_CONTAINED_BY -> rBitmap.contains(docBitmap);
                            case OVERLAP -> RoaringBitmap.intersects(docBitmap, rBitmap);
                            case EQUAL -> docBitmap.equals(rBitmap);
                            case NOT_EQUAL -> !docBitmap.equals(rBitmap);
                        };
                    } catch (IOException e) {
                        throw ExceptionsHelper.convertToOpenSearchException(e);
                    }
                }
            };
        }
    }
}
