package com.factorymind.telemetry.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Contract checks for the Kafka messages of this service, against the JSON Schemas shared by every service in
 * {@code contracts/kafka} (see contracts/README.md). Producers check what they write, consumers check that they
 * can read everything the schema allows, with the same serializer and converter as the running service.
 * The same file exists in every service that uses Kafka (services share no code).
 */
public final class Contracts {

    private static final Path DIR = locate();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private Contracts() {
    }

    /** The producer side: the JSON this message becomes on the topic matches the topic's schema. */
    public static void assertProduces(String topic, Object message) {
        byte[] bytes;
        try (var serializer = new JacksonJsonSerializer<Object>()) {
            bytes = serializer.serialize(topic, message);
        }
        JsonNode json = JSON.readTree(bytes);
        List<Error> errors = schema(topic).validate(json);
        assertThat(errors).as("%s does not match contracts/kafka/%s.schema.json: %s", message, topic, json).isEmpty();
        assertThat(propertyNames(json))
                .as("fields written to %s but not declared in its schema (declare them first)", topic)
                .isSubsetOf(properties(topic).keySet());
    }

    /** The producer side: every value of an enum field of the message is allowed by the schema. */
    public static void assertEnumsDeclared(String topic, Class<? extends Record> type) {
        Map<String, JsonNode> properties = properties(topic);
        for (RecordComponent component : type.getRecordComponents()) {
            if (!component.getType().isEnum()) {
                continue;
            }
            Set<String> sent = new HashSet<>();
            for (Object constant : component.getType().getEnumConstants()) {
                sent.add(((Enum<?>) constant).name());
            }
            assertThat(enumValues(properties.get(component.getName())))
                    .as("%s.%s can send %s; the schema of %s must allow every one", type.getSimpleName(),
                            component.getName(), sent, topic)
                    .containsAll(sent);
        }
    }

    /**
     * The consumer side: the consumer only relies on fields the schema promises, reads every example, gets a value
     * for every required non-null field, accepts every enum value of the schema and ignores fields added later.
     */
    public static void assertConsumes(String topic, Class<? extends Record> type) {
        Map<String, JsonNode> properties = properties(topic);
        Set<String> required = required(topic);
        for (RecordComponent component : type.getRecordComponents()) {
            assertThat(properties)
                    .as("%s reads '%s', which %s does not promise", type.getSimpleName(), component.getName(), topic)
                    .containsKey(component.getName());
        }

        List<JsonNode> examples = examples(topic);
        assertThat(examples).as("examples of %s", topic).isNotEmpty();
        for (JsonNode example : examples) {
            assertThat(schema(topic).validate(example)).as("example of %s: %s", topic, example).isEmpty();
            Record read = read(topic, example, type);
            for (RecordComponent component : type.getRecordComponents()) {
                String name = component.getName();
                if (required.contains(name) && !nullable(properties.get(name))) {
                    assertThat(value(component, read)).as("%s.%s read from %s", type.getSimpleName(), name, example)
                            .isNotNull();
                }
            }
        }

        JsonNode base = examples.getFirst();
        for (RecordComponent component : type.getRecordComponents()) {
            for (String allowed : enumValues(properties.get(component.getName()))) {
                ObjectNode variant = ((ObjectNode) base).deepCopy();
                variant.put(component.getName(), allowed);
                assertThatCode(() -> read(topic, variant, type))
                        .as("%s.%s must accept %s, which %s allows", type.getSimpleName(), component.getName(), allowed, topic)
                        .doesNotThrowAnyException();
                Record read = read(topic, variant, type);
                assertThat(String.valueOf(value(component, read)))
                        .as("%s.%s must accept %s=%s", type.getSimpleName(), component.getName(), component.getName(), allowed)
                        .isEqualTo(allowed);
            }
        }

        ObjectNode newer = ((ObjectNode) base).deepCopy();
        newer.put("fieldAddedLater", "a producer may add optional fields");
        assertThatCode(() -> read(topic, newer, type)).as("%s must ignore unknown fields", type.getSimpleName())
                .doesNotThrowAnyException();
    }

    /** Reads one record value exactly like the listener does: bytes, then the JSON message converter. */
    public static <T> T read(String topic, JsonNode value, Class<T> type) {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(topic, 0, 0L, "key", JSON.writeValueAsBytes(value));
        Object payload = new JacksonJsonMessageConverter().toMessage(record, null, null, type).getPayload();
        assertThat(payload).as("%s read from %s", type.getSimpleName(), value).isInstanceOf(type);
        return type.cast(payload);
    }

    public static List<JsonNode> examples(String topic) {
        Path dir = DIR.resolve("kafka/examples").resolve(topic);
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().map(Contracts::readJson).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Schema schema(String topic) {
        return SCHEMAS.getSchema(schemaNode(topic));
    }

    /** Any JSON Schema file under contracts/, e.g. {@code http/ml-predict/request.schema.json}. */
    public static Schema schemaFile(String relativePath) {
        return SCHEMAS.getSchema(readJson(DIR.resolve(relativePath)));
    }

    public static JsonNode json(String relativePath) {
        return readJson(DIR.resolve(relativePath));
    }

    private static JsonNode schemaNode(String topic) {
        return readJson(DIR.resolve("kafka").resolve(topic + ".schema.json"));
    }

    private static Map<String, JsonNode> properties(String topic) {
        Map<String, JsonNode> properties = new java.util.LinkedHashMap<>();
        schemaNode(topic).get("properties").properties().forEach(e -> properties.put(e.getKey(), e.getValue()));
        return properties;
    }

    private static Set<String> required(String topic) {
        Set<String> required = new HashSet<>();
        schemaNode(topic).get("required").forEach(n -> required.add(n.asString()));
        return required;
    }

    private static boolean nullable(JsonNode property) {
        JsonNode type = property.get("type");
        return type != null && type.isArray() && type.toString().contains("\"null\"");
    }

    private static List<String> enumValues(JsonNode property) {
        List<String> values = new ArrayList<>();
        if (property != null && property.has("enum")) {
            property.get("enum").forEach(v -> {
                if (!v.isNull()) {
                    values.add(v.asString());
                }
            });
        }
        return values;
    }

    private static Set<String> propertyNames(JsonNode json) {
        Set<String> names = new HashSet<>();
        json.properties().forEach(e -> names.add(e.getKey()));
        return names;
    }

    private static Object value(RecordComponent component, Record record) {
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode readJson(Path file) {
        try {
            return JSON.readTree(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** contracts/ at the root of the repository, found from the module directory Maven runs the tests in. */
    private static Path locate() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("contracts/kafka"))) {
                return dir.resolve("contracts");
            }
        }
        throw new IllegalStateException("contracts/kafka not found above " + Path.of("").toAbsolutePath());
    }
}
