# Contracts between services

The services share no code: each one has its own copy of the message classes it sends or reads. That keeps
them independent, but nothing in the compiler stops `maintenance-service` from renaming a field that
`notification-service` reads. These files are the agreement, and tests on both sides check it on every build.

```
contracts/
  kafka/<topic>.schema.json          JSON Schema of the record value on each Kafka topic
  kafka/examples/<topic>/*.json      real-looking messages, read by every consumer's test
  http/ml-predict/                   POST /predict of the Python model: request/response schemas + examples
```

| Topic | Producer | Consumers |
|---|---|---|
| `factorymind.asset.sensor-definitions` | asset-service | telemetry-service, ai-insights-service |
| `factorymind.telemetry.readings` | telemetry-service | telemetry-service, ai-insights-service |
| `factorymind.telemetry.sensor-status` | telemetry-service | alert-service |
| `factorymind.alert.alerts` | alert-service | maintenance-service, ai-insights-service, notification-service |
| `factorymind.maintenance.work-orders` | maintenance-service | alert-service, ai-insights-service, notification-service |
| `factorymind.ai.risk-levels` | ai-insights-service | maintenance-service, notification-service |

## What the tests check

Each service has `src/test/java/.../contract/KafkaContractTest.java` (and a copy of the small `Contracts`
helper). They use the same serializer (`JacksonJsonSerializer`) and converter (`JacksonJsonMessageConverter`) as the
running service, so they test the bytes that really go through Kafka.

**Producer** (`Contracts.assertProduces`, `assertEnumsDeclared`):

- the JSON of the message validates against the topic's schema (types, formats, required fields, enums);
- it writes no field that the schema does not declare;
- every constant of every enum field it can send (e.g. all `WorkOrderPriority` values) is allowed by the schema.

**Consumer** (`Contracts.assertConsumes`):

- it only reads fields the schema promises (a field the producer never sends would always be null);
- it reads every example, and every required non-null field comes out non-null;
- it accepts every enum value the schema allows (a consumer that maps `sensorType` to a Java enum fails here as
  soon as the schema allows a sensor type it does not know);
- it ignores a field it does not know (producers may add optional fields).

**HTTP, Java → Python**: the schemas in `http/ml-predict` are generated from the model's pydantic classes
(`python -m factorymind_ml.contracts`); `ml/predictive-maintenance/tests/test_contracts.py` fails if they are out
of date or if a real answer does not have the shape of `response.json`, and `MlPredictContractTest` in
ai-insights-service checks that the request it sends validates and that it reads every answer.

## Changing a contract without breaking anything

The rule is the usual one for events: **add, never change in place**.

| Change | Safe? | How |
|---|---|---|
| Add an optional field | yes | add it to the schema (not to `required`) and to the producer; consumers ignore it until they need it |
| Add a required field | yes, in two steps | producer first (schema + producer), consumers start reading it afterwards |
| Add an enum value | consumers first | add it to the consumers (or make them read a string), then to the schema and the producer |
| Rename or remove a field, change a type | no | add the new field next to the old one, move every consumer, then remove the old one |

Example: in phase 12 the work-order event got an optional `planId` (the maintenance plan the work order comes
from). It was added to the schema without `required`, the producer sends it, and the consumers' tests read the
new example `examples/factorymind.maintenance.work-orders/3.json` without any change on their side.

The tests turn each row into a build failure on the side that would break: a producer that sends a value the
schema does not allow, or a consumer that cannot read what the schema allows.
