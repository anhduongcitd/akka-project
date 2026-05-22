# Tasks: Online Payment System

**Input**: Design documents from `/specs/001-online-payment/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/http-api.md

**Tests**: Included per Akka SDK conventions - comprehensive test coverage is required

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

Single project structure at repository root:
- `src/main/java/com/example/payment/` - Source code
- `src/test/java/com/example/payment/` - Tests
- `src/main/resources/` - Configuration

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Update package structure from com.example to com.example.payment in pom.xml
- [X] T002 Add Stripe Java SDK dependency (version 24.0.0) to pom.xml
- [X] T003 [P] Add AWS SES SDK dependency (version 1.12.600) to pom.xml
- [X] T004 [P] Configure environment variables in src/main/resources/application.conf (STRIPE_API_KEY, AWS credentials, exchange rate API URL)
- [X] T005 [P] Create .env.example file documenting required environment variables
- [X] T006 [P] Update README.md with setup instructions from quickstart.md

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain model and infrastructure that MUST be complete before ANY user story implementation

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Domain Layer (No Akka Dependencies)

- [X] T007 [P] Create Currency enum in src/main/java/com/example/payment/domain/Currency.java
- [X] T008 [P] Create PaymentStatus enum in src/main/java/com/example/payment/domain/PaymentStatus.java
- [X] T009 [P] Create RefundStatus enum in src/main/java/com/example/payment/domain/RefundStatus.java
- [X] T010 [P] Create CardBrand enum in src/main/java/com/example/payment/domain/CardBrand.java
- [X] T011 Create Money value object with currency support in src/main/java/com/example/payment/domain/Money.java
- [X] T012 [P] Create Customer record in src/main/java/com/example/payment/domain/Customer.java
- [X] T013 [P] Create Refund record in src/main/java/com/example/payment/domain/Refund.java
- [X] T014 Create PaymentMethod record in src/main/java/com/example/payment/domain/PaymentMethod.java
- [X] T015 Create PaymentTransaction record with validation methods in src/main/java/com/example/payment/domain/PaymentTransaction.java
- [X] T016 Create PaymentTransactionEvent sealed interface with 6 event types in src/main/java/com/example/payment/domain/PaymentTransactionEvent.java
- [X] T017 Create PaymentMethodEvent sealed interface with 3 event types in src/main/java/com/example/payment/domain/PaymentMethodEvent.java

### Domain Tests

- [X] T018 [P] Unit test Money value object (add, subtract, convert) in src/test/java/com/example/payment/domain/MoneyTest.java
- [X] T019 [P] Unit test PaymentTransaction validation rules in src/test/java/com/example/payment/domain/PaymentTransactionTest.java

### Infrastructure Services

- [X] T020 Create ExchangeRateService with caching in src/main/java/com/example/payment/application/ExchangeRateService.java
- [X] T021 Create Stripe payment gateway client wrapper in src/main/java/com/example/payment/application/StripePaymentGateway.java
- [X] T022 [P] Create email service wrapper for AWS SES in src/main/java/com/example/payment/application/EmailService.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Complete Payment Transaction (Priority: P1) 🎯 MVP

**Goal**: Customers can make credit/debit card payments and receive confirmation

**Independent Test**: Create payment with test card (4242424242424242), verify transaction succeeds and email sent

### Entity & Workflow

- [X] T023 [US1] Create PaymentTransactionEntity with command handlers in src/main/java/com/example/payment/application/PaymentTransactionEntity.java
- [X] T024 [US1] Implement PaymentProcessingWorkflow with Stripe integration in src/main/java/com/example/payment/application/PaymentProcessingWorkflow.java
- [X] T025 [US1] Create EmailNotificationConsumer listening to PaymentTransactionEntity events in src/main/java/com/example/payment/application/EmailNotificationConsumer.java

### API Layer

- [X] T026 [US1] Create PaymentEndpoint with POST /payment/transactions in src/main/java/com/example/payment/api/PaymentEndpoint.java
- [X] T027 [US1] Add GET /payment/transactions/{id} endpoint to PaymentEndpoint
- [X] T028 [US1] Define request/response records in PaymentEndpoint

### Tests

- [X] T029 [US1] Unit test PaymentTransactionEntity command handlers in src/test/java/com/example/payment/application/PaymentTransactionEntityTest.java
- [X] T030 [US1] Unit test PaymentProcessingWorkflow with mock Stripe in src/test/java/com/example/payment/application/PaymentProcessingWorkflowTest.java
- [X] T031 [US1] Integration test PaymentEndpoint POST /payment/transactions in src/test/java/com/example/payment/api/PaymentEndpointIntegrationTest.java
- [X] T032 [US1] Integration test full payment flow (create → authorize → capture → email) in src/test/java/com/example/payment/api/PaymentFlowIntegrationTest.java

**Checkpoint**: User Story 1 complete - customers can make payments and receive confirmation

---

## Phase 4: User Story 2 - Save Payment Methods (Priority: P2)

**Goal**: Registered customers can save payment methods for faster checkout

**Independent Test**: Save payment method, verify it appears in customer's saved methods list, use it for payment

### Entity & View

- [X] T033 [US2] Create PaymentMethodEntity with command handlers in src/main/java/com/example/payment/application/PaymentMethodEntity.java
- [X] T034 [US2] Create CustomerPaymentMethodsView in src/main/java/com/example/payment/application/CustomerPaymentMethodsView.java
- [X] T035 [US2] Define PaymentMethodEntry record for view in CustomerPaymentMethodsView

### API Layer

- [X] T036 [US2] Create PaymentMethodEndpoint with POST /payment/methods in src/main/java/com/example/payment/api/PaymentMethodEndpoint.java
- [X] T037 [US2] Add GET /payment/methods endpoint to PaymentMethodEndpoint
- [X] T038 [US2] Add DELETE /payment/methods/{id} endpoint to PaymentMethodEndpoint
- [X] T039 [US2] Add PUT /payment/methods/{id}/default endpoint to PaymentMethodEndpoint
- [X] T040 [US2] Define request/response records in PaymentMethodEndpoint

### Integration with US1

- [X] T041 [US2] Update PaymentEndpoint to accept paymentMethodId in payment request
- [X] T042 [US2] Update PaymentProcessingWorkflow to handle saved payment methods

### Tests

- [X] T043 [US2] Unit test PaymentMethodEntity command handlers in src/test/java/com/example/payment/application/PaymentMethodEntityTest.java
- [X] T044 [US2] Integration test CustomerPaymentMethodsView in src/test/java/com/example/payment/application/CustomerPaymentMethodsViewIntegrationTest.java
- [X] T045 [US2] Integration test PaymentMethodEndpoint CRUD operations in src/test/java/com/example/payment/api/PaymentMethodEndpointIntegrationTest.java
- [X] T046 [US2] Integration test payment with saved method in src/test/java/com/example/payment/api/SavedPaymentMethodFlowIntegrationTest.java

**Checkpoint**: User Stories 1 AND 2 work independently - saved methods enable faster checkout

---

## Phase 5: User Story 3 - View Payment History (Priority: P3)

**Goal**: Customers can view transaction history with filtering and download receipts

**Independent Test**: Make several payments, query history with filters, verify all transactions listed correctly

### View & Receipt Generation

- [ ] T047 [US3] Create PaymentHistoryView with query methods in src/main/java/com/example/payment/application/PaymentHistoryView.java
- [ ] T048 [US3] Define PaymentHistoryEntry record for view in PaymentHistoryView
- [ ] T049 [US3] Create ReceiptGenerator service for PDF generation in src/main/java/com/example/payment/application/ReceiptGenerator.java

### API Layer

- [ ] T050 [US3] Add GET /payment/history endpoint to PaymentEndpoint with query params
- [ ] T051 [US3] Add GET /payment/transactions/{id}/receipt endpoint to PaymentEndpoint
- [ ] T052 [US3] Define query response records in PaymentEndpoint

### Tests

- [ ] T053 [US3] Integration test PaymentHistoryView queries in src/test/java/com/example/payment/application/PaymentHistoryViewIntegrationTest.java
- [ ] T054 [US3] Integration test payment history endpoint with filters in src/test/java/com/example/payment/api/PaymentHistoryIntegrationTest.java
- [ ] T055 [US3] Integration test receipt download in src/test/java/com/example/payment/api/ReceiptDownloadIntegrationTest.java

**Checkpoint**: All three user stories work independently - full payment, saved methods, and history

---

## Phase 6: User Story 4 - Process Refunds (Priority: P2)

**Goal**: Merchants can initiate full/partial refunds, customers notified automatically

**Independent Test**: Make payment, initiate refund from merchant portal, verify refund processed and email sent

### Workflow & Consumer

- [ ] T056 [US4] Create RefundWorkflow in src/main/java/com/example/payment/application/RefundWorkflow.java
- [ ] T057 [US4] Update EmailNotificationConsumer to handle refund events

### API Layer

- [ ] T058 [US4] Create RefundEndpoint with POST /payment/transactions/{id}/refunds in src/main/java/com/example/payment/api/RefundEndpoint.java
- [ ] T059 [US4] Add GET /payment/transactions/{id}/refunds endpoint to RefundEndpoint
- [ ] T060 [US4] Define request/response records in RefundEndpoint

### Integration with US1 & US3

- [ ] T061 [US4] Update PaymentTransactionEntity to handle refund events
- [ ] T062 [US4] Update PaymentHistoryView to show refund status

### Tests

- [ ] T063 [US4] Unit test RefundWorkflow with mock Stripe in src/test/java/com/example/payment/application/RefundWorkflowTest.java
- [ ] T064 [US4] Integration test RefundEndpoint in src/test/java/com/example/payment/api/RefundEndpointIntegrationTest.java
- [ ] T065 [US4] Integration test full refund flow (payment → refund → notification) in src/test/java/com/example/payment/api/RefundFlowIntegrationTest.java

**Checkpoint**: All four user stories complete - full payment lifecycle with refunds

---

## Phase 7: Multi-Currency Support

**Purpose**: Enable payments in multiple currencies with real-time exchange rates

### API Layer

- [ ] T066 [P] Add GET /payment/exchange-rates endpoint to PaymentEndpoint
- [ ] T067 [P] Add POST /payment/convert endpoint to PaymentEndpoint
- [ ] T068 Update PaymentEndpoint to accept currency in payment request
- [ ] T069 Update PaymentProcessingWorkflow to handle currency conversion

### Tests

- [ ] T070 [P] Integration test currency conversion in src/test/java/com/example/payment/api/CurrencyConversionIntegrationTest.java
- [ ] T071 [P] Integration test multi-currency payment in src/test/java/com/example/payment/api/MultiCurrencyPaymentIntegrationTest.java

---

## Phase 8: Edge Cases & Error Handling

**Purpose**: Handle timeout, duplicate detection, card expiration warnings

### Implementation

- [ ] T072 [P] Implement duplicate payment detection in PaymentTransactionEntity
- [ ] T073 [P] Add payment timeout handling in PaymentProcessingWorkflow (3 minute timeout)
- [ ] T074 [P] Add card expiration warning logic in CustomerPaymentMethodsView
- [ ] T075 [P] Add comprehensive error responses in all endpoints

### Tests

- [ ] T076 [P] Test duplicate payment detection in src/test/java/com/example/payment/application/DuplicateDetectionTest.java
- [ ] T077 [P] Test payment timeout scenario in src/test/java/com/example/payment/application/PaymentTimeoutTest.java
- [ ] T078 [P] Test card expiration warning in src/test/java/com/example/payment/application/CardExpirationTest.java

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

### Security & Compliance

- [ ] T079 [P] Add @Acl annotations to all endpoints (customer vs merchant access)
- [ ] T080 [P] Implement rate limiting per endpoint (10 req/min payments, 60 req/min queries)
- [ ] T081 [P] Add JWT validation in all endpoints
- [ ] T082 [P] Security audit: verify no raw card data logging

### Documentation & Deployment

- [ ] T083 [P] Update README.md with API documentation
- [ ] T084 [P] Create deployment guide in docs/deployment.md
- [ ] T085 [P] Add OpenAPI/Swagger annotations to endpoints
- [ ] T086 [P] Create developer quickstart based on quickstart.md

### Performance & Monitoring

- [ ] T087 [P] Add performance logging for payment processing duration
- [ ] T088 [P] Add metrics for payment success/failure rates
- [ ] T089 [P] Optimize PaymentHistoryView queries with indexes

### Final Validation

- [ ] T090 Run complete test suite (mvn verify)
- [ ] T091 Run quickstart.md manual testing scenarios
- [ ] T092 Load test with 1000 concurrent payments
- [ ] T093 Verify all acceptance criteria from spec.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational - No dependencies on other stories
- **User Story 2 (Phase 4)**: Depends on Foundational - Independent but integrates with US1
- **User Story 3 (Phase 5)**: Depends on Foundational - Independent but displays US1 data
- **User Story 4 (Phase 6)**: Depends on Foundational - Independent but modifies US1 data
- **Multi-Currency (Phase 7)**: Depends on US1 completion - Enhances US1
- **Edge Cases (Phase 8)**: Depends on US1-US4 completion - Hardens all stories
- **Polish (Phase 9)**: Depends on all user stories - Final improvements

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - FULLY INDEPENDENT
- **User Story 2 (P2)**: Can start after Foundational - Integrates with US1 but testable independently
- **User Story 3 (P3)**: Can start after Foundational - Reads US1 data but testable independently
- **User Story 4 (P2)**: Can start after Foundational - Modifies US1 data but testable independently

### Within Each User Story

- Domain model → Entity/Workflow → API endpoints → Tests
- Entity before Endpoint (endpoints call entities)
- Workflows before integration tests
- Unit tests can run parallel to implementation
- Integration tests after components complete

### Parallel Opportunities

**Phase 1 - Setup** (all tasks [P] can run in parallel):
- T002, T003, T004, T005, T006 can all run concurrently

**Phase 2 - Foundational**:
- All enum creation (T007-T010) can run in parallel
- All domain records (T012-T014) can run in parallel after Money (T011)
- Domain tests (T018, T019) can run in parallel
- Infrastructure services (T020-T022) can run in parallel after domain model

**After Foundational Phase Completes**:
- User Stories 1, 2, 3, 4 can ALL start in parallel (different developers)
- Each story is independently implementable and testable

---

## Parallel Example: User Story 1

```bash
# All US1 tests can run in parallel:
Task T029: Unit test PaymentTransactionEntity
Task T030: Unit test PaymentProcessingWorkflow
Task T031: Integration test PaymentEndpoint
Task T032: Integration test full payment flow
```

---

## Parallel Example: After Foundational

```bash
# Different developers work on different stories simultaneously:
Developer A: User Story 1 (T023-T032)
Developer B: User Story 2 (T033-T046)
Developer C: User Story 3 (T047-T055)
Developer D: User Story 4 (T056-T065)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (6 tasks)
2. Complete Phase 2: Foundational (22 tasks) - CRITICAL
3. Complete Phase 3: User Story 1 (10 tasks)
4. **STOP and VALIDATE**: Test payments with test cards
5. Deploy MVP and collect feedback

**MVP Delivers**: Basic payment processing with email confirmation

### Incremental Delivery

1. **Foundation** (Phases 1-2) → Core infrastructure ready
2. **+ User Story 1** (Phase 3) → MVP: Accept payments ✅
3. **+ User Story 2** (Phase 4) → Saved payment methods for faster checkout ✅
4. **+ User Story 3** (Phase 5) → Payment history and receipts ✅
5. **+ User Story 4** (Phase 6) → Refund processing ✅
6. **+ Multi-Currency** (Phase 7) → International payments ✅
7. **+ Edge Cases** (Phase 8) → Production hardening ✅
8. **+ Polish** (Phase 9) → Security, docs, monitoring ✅

Each phase adds value without breaking previous functionality.

### Parallel Team Strategy

With 4 developers after Foundational phase:

```
Dev A: User Story 1 → Core payment flow
Dev B: User Story 2 → Saved payment methods
Dev C: User Story 3 → Payment history
Dev D: User Story 4 → Refunds

Integration: Stories merge independently, test together
```

---

## Task Summary

**Total Tasks**: 93
**MVP Tasks (Phases 1-3)**: 38 tasks
**Full Feature Tasks**: 93 tasks

**Tasks by Phase**:
- Phase 1 (Setup): 6 tasks
- Phase 2 (Foundational): 22 tasks
- Phase 3 (US1 - Payment): 10 tasks
- Phase 4 (US2 - Saved Methods): 14 tasks
- Phase 5 (US3 - History): 9 tasks
- Phase 6 (US4 - Refunds): 10 tasks
- Phase 7 (Multi-Currency): 6 tasks
- Phase 8 (Edge Cases): 7 tasks
- Phase 9 (Polish): 9 tasks

**Parallel Opportunities**: 47 tasks marked [P] can run concurrently (50%)

**Independent Stories**: All 4 user stories can be developed in parallel after Foundational phase

---

## Notes

- All Akka SDK patterns follow AGENTS.md conventions
- Event Sourced Entities for audit trail (PCI DSS requirement)
- Workflows for multi-step processes with compensation
- Views for efficient querying
- HTTP Endpoints with @Acl security
- Comprehensive test coverage at all levels
- Each user story delivers standalone value
- MVP ready after Phase 3 (38 tasks)
