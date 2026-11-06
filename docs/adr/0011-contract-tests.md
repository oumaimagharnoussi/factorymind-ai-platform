# ADR 0011: Contract tests with shared JSON Schemas

- Status: accepted
- Date: 2026-11-06

## Context

Six Kafka topics connect six services, and ai-insights-service calls the Python model over HTTP. The services
share no code: each keeps its own copy of the message classes, often reduced to the fields
it needs and with strings instead of enums. That keeps them independent, but a breaking change is invisible
until runtime:

- asset-service adds a sensor type, and telemetry-service and alert-service (which map it to an enum) can no
  longer read those records; they go to the dead-letter topic;
- alert-service renames `unit`, and three consumers silently read `null`;
- the model renames `hoursToCritical`, and the AI risk center shows nothing.

Integration tests with Testcontainers cover each service with its own copy of the messages, so they pass in
all three cases.

## Decision

**The agreement lives in the repository, in `contracts/`, and both sides test against it on every build.**

- One JSON Schema (draft 2020-12) per Kafka topic, plus examples. The schema lists the fields, their types and
  formats, which may be null, and the allowed enum values. It names the producer and the consumers.
- `contracts/http/ml-predict`: the request and response schemas of `POST /predict`, **generated from the
  model's pydantic classes**, so the Python side cannot drift from them (its test compares them), plus a
  request example and a real answer.
- Each service using Kafka has a `KafkaContractTest`, with the serializer and converter it runs with:
  - as a **producer**, the JSON it writes validates against the schema, declares no extra field, and every enum
    value it can send is allowed;
  - as a **consumer**, it reads only fields the schema promises, reads every example, accepts every enum value
    the schema allows, and ignores unknown fields.
- `MlPredictContractTest` (Java) validates the request it sends and reads the response example;
  `test_contracts.py` (Python) checks that a real answer has the shape of that example.
- The validator is networknt `json-schema-validator` 3.x, which reads Jackson 3 trees like Spring Boot 4. It is
  a test dependency only.

The tests were checked by breaking the contract on purpose: removing `URGENT` from the work-order priorities,
adding a sensor type, removing `unit` from alerts and `hoursToCritical` from the model's answer each fail the
build of the service that would break, with a message naming the field.

Evolution rule (contracts/README.md): add fields, never change them in place. Add enum values consumers first,
and rename or remove a field in several steps.

### Alternatives considered

- **Pact (consumer-driven contracts)**: the reference tool for HTTP, with a broker that records which versions
  are compatible. For Kafka messages in a monorepo released together, a broker and pact files per pair of
  services is more machinery than needed, and the Python side would need its own Pact setup.
- **Confluent Schema Registry with Avro or JSON Schema**: checks compatibility at publish time, in production.
  It is the right tool when several teams and repositories publish to shared topics. Here it would add a
  service to run and a serialization format to every service, for a check the build can already make.
- **A shared Java library of message classes**: it would couple every service's release to the library and would
  not cover the Python model.

## Consequences

- A change that breaks another service fails the build of that service, before it runs anywhere, with a message
  that names the topic and the field.
- The schemas also document the events, in a form other teams or tools can read.
- Adding a field means touching the schema in the same change: a small extra step, and it is on purpose.
- Not covered: HTTP calls between Java services (maintenance → inventory, notification → asset). Their tests mock
  the other side. Pact would be the next step if such calls multiply.
