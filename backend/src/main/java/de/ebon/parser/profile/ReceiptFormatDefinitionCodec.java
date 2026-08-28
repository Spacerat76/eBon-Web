package de.ebon.parser.profile;

import static de.ebon.parser.profile.ProfileValidationError.Code.JSON_SCHEMA;

import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

@Component
public final class ReceiptFormatDefinitionCodec {
    public static final int MAX_JSON_LENGTH = 131_072;

    // Dedicated mapper: application-wide Jackson leniency must not open this schema.
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                            .maxStringLength(MAX_JSON_LENGTH).build())
                    .build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();

    public ReceiptFormatDefinition read(String json) {
        if (json == null || json.length() > MAX_JSON_LENGTH) {
            throw new ProfileDefinitionException(JSON_SCHEMA);
        }
        try {
            ReceiptFormatDefinition result = mapper.readValue(json, ReceiptFormatDefinition.class);
            if (result == null) {
                throw new ProfileDefinitionException(JSON_SCHEMA);
            }
            return result;
        } catch (RuntimeException exception) {
            throw new ProfileDefinitionException(JSON_SCHEMA);
        }
    }

    public String write(ReceiptFormatDefinition definition) {
        if (definition == null) {
            throw new ProfileDefinitionException(JSON_SCHEMA);
        }
        try {
            String json = mapper.writeValueAsString(definition);
            if (json.length() > MAX_JSON_LENGTH) {
                throw new ProfileDefinitionException(JSON_SCHEMA);
            }
            return json;
        } catch (RuntimeException exception) {
            throw new ProfileDefinitionException(JSON_SCHEMA);
        }
    }
}
