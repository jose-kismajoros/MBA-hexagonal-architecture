# MBA Hexagonal Architecture: Cancelamento de Evento

Continuação do projeto do curso (branch `clean-arch` de [devfullcycle/MBA-hexagonal-architecture](https://github.com/devfullcycle/MBA-hexagonal-architecture)). Esta versão adiciona o cancelamento de evento, com a cascata assíncrona que cancela os ingressos reagindo ao evento de domínio `EventCancelled`.

Módulos (Java 17 + Gradle):

- `domain`: agregados, objetos de valor e eventos de domínio, sem framework.
- `application`: casos de uso com Input/Output próprios, que dependem apenas das portas do domínio.
- `infrastructure`: Spring Boot (REST, GraphQL, JPA, outbox, relay e fila).

## Pré-requisitos

- JDK 17. O wrapper usa Gradle 8.2.1, que não roda sobre JDK 21+.
- Docker, para o MySQL usado ao subir a aplicação. Os testes usam H2 em memória e não precisam do Docker.

## Como subir o projeto

```bash
docker compose up -d                 # MySQL 8 em localhost:3306 (database "events", root/root)
./gradlew :infrastructure:bootRun    # API em http://localhost:8080 e GraphiQL em /graphiql
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`.

## Como rodar os testes

```bash
./gradlew test
```

A suíte cobre:

- **Domínio:** `EventTest` e `TicketTest`.
- **Casos de uso com repositórios in-memory:** `CancelEventUseCaseTest`, `GetEventByIdUseCaseTest`, `CancelEventTicketsUseCaseTest` e `SubscribeCustomerToEventUseCaseTest`.
- **Integração (Spring + H2):** `EventDatabaseRepositoryIT`, `TicketDatabaseRepositoryIT`, `EventControllerTest` e `EventResolverTest`.
- **Ponta a ponta do fluxo assíncrono:** `EventCancellationFlowIT`.

## Onde a cascata de cancelamento acontece

O `CancelEventUseCase` chama `event.cancel()`. O agregado `Event` passa para `CANCELLED` e registra o evento de domínio `EventCancelled` (type `event.cancelled`), sem tocar no agregado `Ticket`. Ao persistir, o `EventDatabaseRepository` grava esse evento na tabela `outbox` na mesma transação. O `OutboxRelay` (job agendado) publica o conteúdo no `QueueGateway`, e o `ConsumerQueueGateway` roteia o tipo `event.cancelled` para o `CancelEventTicketsUseCase`. Esse caso de uso busca os ingressos do evento por `TicketRepository.ticketsByEventId` e chama `ticket.cancel()` em cada um. A operação é idempotente: reprocessar a mensagem não quebra o fluxo. O `EventCancellationFlowIT` prova esse caminho de duas formas: publicando o JSON do `EventCancelled` no `ConsumerQueueGateway`, e cancelando via REST e deixando outbox + relay agirem.

## Contratos

### REST

| Operação | Requisição | Respostas |
| --- | --- | --- |
| Cancelar evento | `POST /events/{id}/cancel` | `200 {"id","status":"CANCELLED"}`; `422 Event not found` / `422 Event already cancelled` |
| Consultar evento | `GET /events/{id}` | `200 {"id","name","date","totalSpots","status"}`; `404` sem corpo |
| Consultar evento (público) | `GET /events/{id}` com header `X-Public: true` | `200 {"id","status"}`; `404` sem corpo |
| Inscrever cliente | `POST /events/{id}/subscribe` | `422 Event is cancelled` quando o evento está cancelado |

A consulta passa pelo padrão presenter: `GetEventByIdResponseEntity` (completo) e `PublicGetEventByIdResponseEntity` (reduzido), alternados pelo header `X-Public` no `EventController`.

### GraphQL

```graphql
mutation { cancelEvent(id: "<id>") { id status } }
query    { eventOfId(id: "<id>") { id name date totalSpots status } }
```
