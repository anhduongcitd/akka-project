# AI Documentation & Development Guideline
**Online Payment Service - Akka SDK 3.5+ Project**

**Last Updated**: May 24, 2026  
**Project Status**: Production-Ready (127/127 tests passing)

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture & Package Structure](#architecture--package-structure)
3. [Core Component Guidelines](#core-component-guidelines)
4. [AI Agent Development](#ai-agent-development)
5. [Testing Strategies](#testing-strategies)
6. [Code Organization & Patterns](#code-organization--patterns)
7. [Security & Compliance](#security--compliance)
8. [Best Practices & Gotchas](#best-practices--gotchas)
9. [Development Workflow](#development-workflow)
10. [Common Problems & Solutions](#common-problems--solutions)

---

## Project Overview

### What is This Project?

This is a **Production-Ready Payment Processing Service** built with Akka SDK that handles:

- **Payment Processing**: Credit/debit card payments via Stripe gateway
- **Payment Methods**: Save and manage payment methods securely
- **Payment History**: Query and filter transaction history
- **Refunds**: Full and partial refund processing
- **Multi-Currency**: USD, EUR, GBP, JPY, AUD with real-time exchange rates
- **PCI DSS Compliance**: Enterprise-grade security with tokenization
- **AI Agents**: Intelligent customer support, fraud detection, and payment analysis

### Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Akka SDK | 3.5.19 | Core framework for building components |
| Stripe | 24.0.0 | Payment gateway integration |
| AWS SES | 1.12.600 | Email notifications |
| Java | 21+ | Language runtime |
| Maven | 3.9+ | Build automation |

### Current Metrics

- **Total Tests**: 127 (all passing)
- **Components**: 40+ (Entities, Views, Workflows, Agents, Endpoints)
- **AI Agents**: 9 (Support, Fraud Detection, Analytics, Planning, Orchestration)
- **Code Lines**: 15,000+ production code
- **Security**: PCI DSS Level 1, JWT Auth, Rate Limiting, Audit Logging

---

## Architecture & Package Structure

### Package Organization

Follow strict hierarchical package structure with NO circular dependencies:

```
com.example.payment/
├── domain/                          # Pure business logic (NO Akka deps)
│   ├── PaymentTransaction.java      # State records
│   ├── PaymentTransactionEvent.java # Sealed event interface
│   ├── PaymentMethod.java
│   ├── RefundEligibility.java
│   └── ...
├── application/                     # Akka components
│   ├── PaymentTransactionEntity.java
│   ├── PaymentProcessingWorkflow.java
│   ├── PaymentHistoryView.java
│   ├── EmailNotificationConsumer.java
│   ├── RateLimitEntity.java
│   └── ...
├── agents/                          # AI agents
│   ├── CustomerSupportAgent.java
│   ├── FraudAnalystAgent.java
│   ├── PaymentAssistantAgent.java
│   ├── PlannerAgent.java
│   ├── SummarizerAgent.java
│   ├── DynamicAgentWorkflow.java
│   ├── AgentOrchestrationWorkflow.java
│   ├── guardrails/                  # Agent safety measures
│   │   ├── PiiDetectionGuardrail.java
│   │   ├── OutputValidationGuardrail.java
│   │   └── AuditLoggingGuardrail.java
│   ├── api/                         # Agent-specific APIs
│   │   └── AgentEndpoint.java
│   └── domain/                      # Agent data models
├── api/                             # HTTP/gRPC endpoints (NO @Component)
│   ├── PaymentEndpoint.java
│   ├── PaymentMethodEndpoint.java
│   ├── RefundEndpoint.java
│   └── ...
├── Bootstrap.java                   # Service entry point (implements ServiceSetup)
└── ...
```

### Dependency Rules

**✅ ALLOWED**:
- `domain` → (nothing else)
- `application` → `domain`
- `agents` → `application`, `domain`
- `api` → `application`, `domain`, `agents`
- `Bootstrap` → everything

**❌ FORBIDDEN**:
- `domain` → `application` or `api`
- `application` → `api`
- Circular dependencies

---

## Core Component Guidelines

### 1. Domain Model (com.example.payment.domain)

**Purpose**: Pure business logic with NO Akka dependencies

```java
// ✅ GOOD: Domain object with business logic
public record PaymentTransaction(
    String transactionId,
    String customerId,
    BigDecimal amount,
    String currency,
    String status,  // PENDING, SUCCEEDED, FAILED
    Instant createdAt
) {
    // Business logic methods
    public boolean canBeRefunded() {
        return status.equals("SUCCEEDED") && 
               Instant.now().isBefore(createdAt.plus(Duration.ofDays(90)));
    }

    public PaymentTransaction markSucceeded(Instant succeededAt) {
        return new PaymentTransaction(
            transactionId, customerId, amount, currency, 
            "SUCCEEDED", createdAt  // Immutable - return new instance
        );
    }

    public boolean validateAmount() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
}

// ✅ GOOD: Sealed event hierarchy
public sealed interface PaymentTransactionEvent permits
    PaymentStarted,
    PaymentSucceeded,
    PaymentFailed {
    
    String transactionId();
}

@TypeName("payment.started")
public record PaymentStarted(
    String transactionId,
    String customerId,
    BigDecimal amount,
    String currency
) implements PaymentTransactionEvent {}

@TypeName("payment.succeeded")
public record PaymentSucceeded(
    String transactionId,
    String stripeChargeId,
    Instant succeededAt
) implements PaymentTransactionEvent {}
```

**Key Rules**:
- ✅ Use Java `record` for immutability
- ✅ Add business logic methods (`canBeRefunded()`, `validate*()`)
- ✅ Use `with*()` methods for updates (return new instance)
- ✅ Events have `@TypeName` annotation
- ❌ NO imports from `akka.javasdk`
- ❌ NO side effects or I/O operations
- ❌ NO mutable state

### 2. Event Sourced Entities (com.example.payment.application)

**Purpose**: Manage state with event sourcing and persist state changes

```java
@Component(id = "payment-transaction")
public class PaymentTransactionEntity extends EventSourcedEntity<
    PaymentTransaction,  // State (from domain)
    PaymentTransactionEvent  // Event (from domain)
> {

    @Override
    public PaymentTransaction emptyState() {
        // Return initial state for new entity
        return new PaymentTransaction(
            "", "", BigDecimal.ZERO, "", "PENDING", Instant.EPOCH
        );
    }

    // Command handler
    public Effect<PaymentTransactionEntity.StartPaymentResponse> startPayment(
        PaymentTransactionEntity.StartPaymentRequest request
    ) {
        // 1. Validate
        if (!request.amount().validate()) {
            return effects().error("Invalid amount");
        }

        // 2. Create event
        var event = new PaymentStarted(
            request.transactionId(),
            request.customerId(),
            request.amount(),
            request.currency()
        );

        // 3. Persist and reply
        return effects()
            .persist(event)
            .thenReply(newState -> new StartPaymentResponse(
                newState.transactionId(),
                newState.status()
            ));
    }

    // Event handler - pure function
    @Override
    public PaymentTransaction applyEvent(PaymentTransactionEvent event) {
        return switch (event) {
            case PaymentStarted ps -> 
                new PaymentTransaction(
                    ps.transactionId(),
                    ps.customerId(),
                    ps.amount(),
                    ps.currency(),
                    "PENDING",
                    Instant.now()
                );
            case PaymentSucceeded ps ->
                currentState().markSucceeded(ps.succeededAt());
            case PaymentFailed pf ->
                currentState().withStatus("FAILED");
        };
    }

    // Request/Response records
    public record StartPaymentRequest(
        String transactionId,
        String customerId,
        BigDecimal amount,
        String currency
    ) {}

    public record StartPaymentResponse(
        String transactionId,
        String status
    ) {}
}
```

**Key Rules**:
- ✅ Extends `EventSourcedEntity<State, Event>`
- ✅ Has `@Component(id = "...")` annotation
- ✅ `emptyState()` returns initial state
- ✅ Command handlers accept 1 parameter, return `Effect<T>`
- ✅ `applyEvent()` is pure function (no side effects)
- ✅ Events are persisted before reply
- ❌ NO business logic in command handlers (put in domain)
- ❌ NO side effects in `applyEvent()`
- ❌ Command handler parameter can't be null

### 3. Key-Value Entities (com.example.payment.application)

**Purpose**: Simple state management without event sourcing

```java
@Component(id = "fraud-check")
public class FraudCheckEntity extends KeyValueEntity<FraudCheckState> {

    @Override
    public FraudCheckState emptyState() {
        return new FraudCheckState("", List.of(), 0);
    }

    public Effect<Done> updateFraudScore(UpdateFraudScoreRequest request) {
        var newState = currentState()
            .addViolation(request.violation())
            .withScore(currentState().calculateScore());

        return effects()
            .updateState(newState)
            .thenReply(Done.getInstance());
    }

    public record FraudCheckState(
        String transactionId,
        List<String> violations,
        int riskScore
    ) {
        public FraudCheckState addViolation(String violation) {
            var updated = new ArrayList<>(violations);
            updated.add(violation);
            return new FraudCheckState(transactionId, updated, riskScore);
        }

        public FraudCheckState withScore(int score) {
            return new FraudCheckState(transactionId, violations, score);
        }

        public int calculateScore() {
            return violations.size() * 10;
        }
    }

    public record UpdateFraudScoreRequest(String violation) {}
}
```

**Key Rules**:
- ✅ Extends `KeyValueEntity<State>`
- ✅ Use for simple state (no audit trail needed)
- ✅ `updateState()` directly replaces state
- ✅ No events - direct state updates
- ❌ Less suitable for financial audit requirements

### 4. Views (com.example.payment.application)

**Purpose**: Query models that consume events from entities

```java
@Component(id = "payment-history-view")
public class PaymentHistoryView extends View {

    // ✅ GOOD: TableUpdater for ESE views (uses onEvent)
    @Component(id = "payment-history-table")
    public static class PaymentHistoryTable extends TableUpdater<PaymentHistoryRow> {

        @Subscribe.EventSourcedEntity(PaymentTransactionEntity.class)
        public Effect<PaymentHistoryRow> onEvent(PaymentTransactionEvent event) {
            var entityId = updateContext().eventSubject().orElse("");

            return switch (event) {
                case PaymentStarted ps ->
                    effects().updateRow(new PaymentHistoryRow(
                        entityId,
                        ps.customerId(),
                        ps.amount(),
                        ps.currency(),
                        "PENDING",
                        ps.createdAt,
                        Instant.EPOCH
                    ));
                case PaymentSucceeded ps ->
                    effects().updateRow(rowState().withStatus("SUCCEEDED")
                        .withSucceededAt(ps.succeededAt()));
                case PaymentFailed pf ->
                    effects().updateRow(rowState().withStatus("FAILED"));
            };
        }
    }

    // Query methods
    @Query("SELECT * AS items FROM payment_history WHERE customer_id = :customerId")
    public QueryEffect<PaymentHistoriesResponse> getByCustomerId(String customerId) {
        // Handled by framework
        return QueryEffect.ok(new PaymentHistoriesResponse(List.of()));
    }

    // Row and response records
    public record PaymentHistoryRow(
        String transactionId,
        String customerId,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        Instant succeededAt
    ) {}

    public record PaymentHistoriesResponse(List<PaymentHistoryRow> items) {}
}
```

**Key Rules**:
- ✅ Extends `View`
- ✅ ESE views use `onEvent(Event)` in TableUpdater
- ✅ KVE views use `onUpdate(State)` in TableUpdater
- ✅ Multi-row queries return wrapper with `List<Row> items`
- ✅ Use `SELECT * AS items FROM table WHERE ...`
- ❌ Don't use `@Consume` on View class (use TableUpdater)
- ❌ Row records should be public (named `Entry` or specific name)

### 5. Workflows (com.example.payment.application)

**Purpose**: Orchestrate multi-step processes with compensation and recovery

```java
@Component(id = "payment-processing")
public class PaymentProcessingWorkflow extends Workflow<PaymentProcessingWorkflow.State> {

    private final ComponentClient componentClient;
    private final StripePaymentGateway stripeGateway;

    public PaymentProcessingWorkflow(
        ComponentClient componentClient,
        StripePaymentGateway stripeGateway
    ) {
        this.componentClient = componentClient;
        this.stripeGateway = stripeGateway;
    }

    // State record
    public record State(
        String transactionId,
        String status,  // INITIATED, CHARGED, CONFIRMED, FAILED
        String stripeChargeId
    ) {
        public State withStatus(String newStatus) {
            return new State(transactionId, newStatus, stripeChargeId);
        }
    }

    // Workflow settings
    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
            .defaultStepTimeout(Duration.ofSeconds(5))
            .stepRecovery(
                PaymentProcessingWorkflow::chargeStep,
                RecoverStrategy.maxRetries(2)
                    .failoverTo(PaymentProcessingWorkflow::compensateChargeStep)
            )
            .build();
    }

    // Command handlers (use effects())
    public Effect<String> startPayment(String transactionId) {
        return effects()
            .updateState(new State(transactionId, "INITIATED", ""))
            .transitionTo(PaymentProcessingWorkflow::chargeStep)
            .thenReply("Payment processing started");
    }

    // Step methods (use stepEffects())
    @StepName("charge")
    private StepEffect chargeStep() {
        var payment = currentState();
        var chargeId = stripeGateway.charge(payment.transactionId());

        return stepEffects()
            .updateState(currentState()
                .withStatus("CHARGED")
                .withStripeChargeId(chargeId))
            .thenTransitionTo(PaymentProcessingWorkflow::confirmStep);
    }

    @StepName("confirm")
    private StepEffect confirmStep() {
        // Confirm charge in payment service
        var newState = currentState().withStatus("CONFIRMED");
        return stepEffects()
            .updateState(newState)
            .thenEnd();
    }

    // Compensation for failure recovery
    @StepName("compensate-charge")
    private StepEffect compensateChargeStep() {
        // Refund the charge if we charged but couldn't confirm
        if (!currentState().stripeChargeId.isEmpty()) {
            stripeGateway.refund(currentState().stripeChargeId);
        }

        return stepEffects()
            .updateState(currentState().withStatus("FAILED"))
            .thenEnd();
    }
}
```

**Key Rules**:
- ✅ Extends `Workflow<State>`
- ✅ Defines `settings()` with `WorkflowSettings` (NOT deprecated `definition()`)
- ✅ Uses `@StepName("name")` on step methods
- ✅ Command handlers: accept 1 param, return `Effect<T>`, use `effects()`
- ✅ Step methods: accept 0-1 param, return `StepEffect`, use `stepEffects()`
- ✅ Step timeout for agent calls: **60 seconds minimum**
- ✅ Limited retries for agent calls: **max 2 retries**
- ✅ Compensation via `failoverTo()`
- ❌ Never use deprecated `definition()` method
- ❌ Don't use string step names (use method references `::`)
- ❌ Step methods must return `StepEffect` (not `Effect`)

### 6. Consumers (com.example.payment.application)

**Purpose**: React to events from entities and publish to other services

```java
@Component(id = "email-notification-consumer")
@Consume.FromEventSourcedEntity(PaymentTransactionEntity.class)
public class EmailNotificationConsumer extends Consumer {

    private final EmailService emailService;

    public EmailNotificationConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    public Effect onEvent(PaymentTransactionEvent event) {
        return switch (event) {
            case PaymentSucceeded ps ->
                handlePaymentSucceeded(ps);
            case PaymentFailed pf ->
                handlePaymentFailed(pf);
            default ->
                effects().ignore();
        };
    }

    private Effect handlePaymentSucceeded(PaymentSucceeded event) {
        var customerId = messageContext().eventSubject().get();
        // Send email asynchronously
        emailService.sendSuccessEmail(customerId, event.amount());
        return effects().done();
    }

    private Effect handlePaymentFailed(PaymentFailed event) {
        var customerId = messageContext().eventSubject().get();
        emailService.sendFailureEmail(customerId);
        return effects().done();
    }
}

// Producer to Topic
@Component(id = "counter-to-topic")
@Consume.FromEventSourcedEntity(PaymentTransactionEntity.class)
@Produce.ToTopic("payment-events")
public class PaymentToTopicConsumer extends Consumer {
    
    public Effect onEvent(PaymentTransactionEvent event) {
        var transactionId = messageContext().eventSubject().get();
        Metadata metadata = Metadata.EMPTY
            .add("ce-subject", transactionId);
        return effects().produce(event, metadata);
    }
}
```

**Key Rules**:
- ✅ Extends `Consumer`
- ✅ Annotated with `@Consume.From*` (EventSourcedEntity, KeyValueEntity, Topic, etc.)
- ✅ Returns `effects().done()` or `effects().ignore()`
- ✅ Add `ce-subject` metadata when producing to topics
- ✅ Can consume from multiple sources with multiple handlers
- ❌ Don't have complex business logic (keep it simple)

---

## AI Agent Development

### 1. Agent Architecture Overview

This project uses **5 specialized agents** orchestrated by 2 workflow components:

```
┌─────────────────────────────────────────┐
│  Agent Orchestration Request (HTTP)     │
└─────────────────┬───────────────────────┘
                  │
        ┌─────────▼──────────┐
        │ Agent Endpoint     │
        └─────────┬──────────┘
                  │
        ┌─────────▼──────────────────────────┐
        │ DynamicAgentWorkflow               │
        │ (Multi-agent orchestration)        │
        │ - Calls PlannerAgent to decide     │
        │ - Executes agents in sequence      │
        │ - Calls SummarizerAgent for result │
        └─────────┬──────────────────────────┘
                  │
    ┌─────────────┼─────────────┬────────────────┐
    │             │             │                │
    ▼             ▼             ▼                ▼
CustomerSupport  Fraud      Payment         [Other agents]
Agent            Analyst    Assistant
                 Agent      Agent
```

### 2. Agent Implementation Pattern

```java
@Component(id = "customer-support")
public class CustomerSupportAgent extends Agent {

    private final ComponentClient componentClient;

    public CustomerSupportAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record SupportRequest(
        String customerId,
        String query
    ) {}

    public record SupportResponse(
        String answer,
        String action,              // INFORM, REFUND_ELIGIBLE, ESCALATE
        String confidence,          // HIGH, MEDIUM, LOW
        String transactionId,
        RefundRecommendation refund
    ) {}

    // Main command handler - ONLY ONE PER AGENT
    public Effect<SupportResponse> handleQuery(SupportRequest request) {
        String systemPrompt = """
            You are a helpful payment support agent.
            Be concise and helpful. If unsure, escalate to human support.
            """;

        return effects()
            .systemMessage(systemPrompt)
            .userMessage(request.query)
            .tools()  // Add function tools
                .addTool(this::getPaymentHistory)
                .addTool(this::getRefundEligibility)
            .endTools()
            .responseConformsTo(SupportResponse.class)  // Type-safe response
            .onFailure(throwable ->
                new SupportResponse(
                    "Unable to process request",
                    "ESCALATE",
                    "LOW",
                    "",
                    null
                ))
            .thenReply();
    }

    // Function Tools - Available to LLM
    @FunctionTool(
        description = "Get customer's payment history"
    )
    private String getPaymentHistory(String customerId) {
        var view = componentClient.forView()
            .method(PaymentHistoryView::getByCustomerId)
            .invoke(customerId);
        return formatPaymentHistory(view);
    }

    @FunctionTool(
        description = "Check if transaction is eligible for refund"
    )
    private String getRefundEligibility(String transactionId) {
        var entity = componentClient
            .forEventSourcedEntity("payment-transaction")
            .method(PaymentTransactionEntity::checkRefund)
            .invoke(transactionId);
        return entity.eligible ? "Yes" : "No";
    }
}
```

**Key Rules**:
- ✅ Extends `Agent`
- ✅ Has `@Component(id = "...")` annotation
- ✅ **ONLY ONE command handler method** per agent
- ✅ Use `systemMessage()` + `userMessage()` builder
- ✅ Add function tools via `.tools().addTool()`
- ✅ Use `responseConformsTo(Class.class)` for structured responses
- ✅ Handle errors with `.onFailure()`
- ✅ Session ID typically UUID for new interactions
- ✅ Stateless design (no mutable state)
- ❌ Don't create multiple command handlers
- ❌ Don't use mutable state in agent
- ❌ Don't ignore `.onFailure()` handling

### 3. Agent Session Memory Management

```java
// Session memory is automatic - keyed by sessionId
// Use different session IDs for different conversations

// New conversation
String sessionId = UUID.randomUUID().toString();
componentClient
    .forAgent()
    .inSession(sessionId)
    .method(CustomerSupportAgent::handleQuery)
    .invoke(request);

// Shared session between agents (same conversation)
String sessionId = UUID.randomUUID().toString();

// Agent 1 call
componentClient
    .forAgent()
    .inSession(sessionId)  // Same session
    .method(FraudAnalystAgent::analyze)
    .invoke(request1);

// Agent 2 call - has context from Agent 1
componentClient
    .forAgent()
    .inSession(sessionId)  // Same session
    .method(CustomerSupportAgent::handleQuery)
    .invoke(request2);
```

**Memory Configuration**:

```java
// In agent system prompt:
// - Limited window: Last 10 messages
// - Memory cleared per conversation (no cross-session contamination)
// - Session ID = conversation ID

settings().agent()
    .memoryProvider(MemoryProvider.limitedWindow().readLast(10))
```

### 4. Agent Orchestration Workflows

**Pattern 1: Dynamic Agent Selection via PlannerAgent**

```java
@Component(id = "dynamic-agent-team")
public class DynamicAgentWorkflow extends Workflow<DynamicAgentWorkflow.State> {

    public record State(
        String query,
        Plan plan,
        Map<String, String> agentResponses,
        String answer
    ) {}

    public record Plan(List<PlanStep> steps) {}
    public record PlanStep(String agentId, String query) {}

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
            .stepTimeout(PaymentProcessingWorkflow::planStep, Duration.ofSeconds(60))
            .defaultStepRecovery(
                RecoverStrategy.maxRetries(2)
                    .failoverTo(DynamicAgentWorkflow::summarizeStep))
            .build();
    }

    // Step 1: Generate execution plan
    @StepName("plan")
    private StepEffect planStep() {
        var plan = componentClient
            .forAgent()
            .inSession(sessionId())
            .method(PlannerAgent::createPlan)
            .invoke(currentState().query);

        return stepEffects()
            .updateState(currentState().withPlan(plan))
            .thenTransitionTo(DynamicAgentWorkflow::executePlanStep);
    }

    // Step 2: Execute plan sequentially
    @StepName("execute-plan")
    private StepEffect executePlanStep() {
        var step = currentState().nextStep();

        var response = componentClient
            .forAgent()
            .inSession(sessionId())
            .dynamicCall(step.agentId())  // Don't know type at compile time
            .invoke(step.query());

        var newState = currentState().addResponse(step.agentId(), response);

        if (newState.hasMoreSteps()) {
            return stepEffects()
                .updateState(newState)
                .thenTransitionTo(DynamicAgentWorkflow::executePlanStep);
        } else {
            return stepEffects()
                .updateState(newState)
                .thenTransitionTo(DynamicAgentWorkflow::summarizeStep);
        }
    }

    // Step 3: Synthesize responses
    @StepName("summarize")
    private StepEffect summarizeStep() {
        var finalAnswer = componentClient
            .forAgent()
            .inSession(sessionId())
            .method(SummarizerAgent::synthesize)
            .invoke(new SummarizerAgent.Request(
                currentState().query,
                currentState().agentResponses.values()
            ));

        return stepEffects()
            .updateState(currentState().withAnswer(finalAnswer))
            .thenEnd();
    }

    private String sessionId() {
        return commandContext().workflowId();
    }
}
```

### 5. Agent Guardrails (Security & Compliance)

```java
// Guardrails are applied automatically via Akka SDK framework
// They enforce rules BEFORE and AFTER LLM responses

@Component(id = "customer-support")
public class CustomerSupportAgent extends Agent {

    public Effect<SupportResponse> handleQuery(SupportRequest request) {
        return effects()
            .systemMessage("You are a payment support agent.")
            .userMessage(request.query)
            // Guardrails automatically applied:
            // 1. PII Detection - Block responses with customer PII
            // 2. Output Validation - Ensure response matches schema
            // 3. Audit Logging - Log all interactions for compliance
            .responseConformsTo(SupportResponse.class)
            .thenReply();
    }
}
```

**Built-in Guardrails**:

1. **PII Detection**
   - Detects personal identifying information in responses
   - Blocks/masks sensitive data
   - Logs violations for audit

2. **Output Validation**
   - Ensures response matches declared schema
   - Rejects malformed responses
   - Retries if validation fails

3. **Audit Logging**
   - Logs all agent interactions
   - Records prompts, responses, function tool calls
   - Immutable audit trail

### 6. Agent Testing Pattern

```java
public class CustomerSupportAgentTest extends TestKitSupport {

    private final TestModelProvider agentModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withModelProvider(CustomerSupportAgent.class, agentModel);
    }

    @Test
    public void testAgentWithMockedResponse() {
        // Fix agent response
        var mockResponse = new SupportResponse(
            "Your transaction was successful",
            "INFORM",
            "HIGH",
            "txn_123",
            null
        );
        agentModel.fixedResponse(JsonSupport.encodeToString(mockResponse));

        // Invoke agent
        var result = componentClient
            .forAgent()
            .inSession("test-session")
            .method(CustomerSupportAgent::handleQuery)
            .invoke(new SupportRequest("cust_123", "Where is my payment?"));

        assertThat(result.answer()).contains("successful");
    }

    @Test
    public void testAgentWithConditionalResponses() {
        // Conditional response based on input
        agentModel.whenMessage(msg -> msg.contains("refund"))
            .reply(JsonSupport.encodeToString(
                new SupportResponse("...", "REFUND_ELIGIBLE", "HIGH", "", null)
            ));

        var result = componentClient
            .forAgent()
            .inSession("test-session")
            .method(CustomerSupportAgent::handleQuery)
            .invoke(new SupportRequest("cust_123", "Can I get a refund?"));

        assertThat(result.action()).isEqualTo("REFUND_ELIGIBLE");
    }
}
```

**Key Testing Rules**:
- ✅ Extend `TestKitSupport`
- ✅ Create `TestModelProvider` instance
- ✅ Register in `testKitSettings()`
- ✅ Use `fixedResponse()` with `JsonSupport.encodeToString()`
- ✅ Use `whenMessage(predicate).reply()` for conditional responses
- ✅ Always use `.inSession(sessionId)` when calling agents
- ❌ Don't test actual LLM (mock all responses)

---

## Testing Strategies

### 1. Entity Unit Tests (Event Sourced)

```java
public class PaymentTransactionEntityTest {

    @Test
    public void testStartPaymentCommand() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        var request = new PaymentTransactionEntity.StartPaymentRequest(
            "txn_123", "cust_123", BigDecimal.valueOf(100), "USD"
        );

        var result = testKit.method(PaymentTransactionEntity::startPayment)
            .invoke(request);

        assertThat(result.isReply()).isTrue();
        var response = result.getReply();
        assertThat(response.transactionId()).isEqualTo("txn_123");
        assertThat(response.status()).isEqualTo("PENDING");

        // Check state was updated
        assertThat(testKit.getState().status()).isEqualTo("PENDING");
    }

    @Test
    public void testEventApplied() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        var event = new PaymentSucceeded(
            "txn_123", "stripe_charge_123", Instant.now()
        );

        testKit.applyEvent(event);

        assertThat(testKit.getState().status()).isEqualTo("SUCCEEDED");
    }
}
```

### 2. View Integration Tests

```java
public class PaymentHistoryViewIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);
    }

    @Test
    public void testViewProjection() {
        var events = testKit.getEventSourcedEntityIncomingMessages(
            PaymentTransactionEntity.class
        );

        // Publish event from entity
        var event = new PaymentStarted(
            "txn_123", "cust_123", BigDecimal.valueOf(100), "USD"
        );
        events.publish(event, "txn_123");

        // Wait for view to process
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomerId)
                    .invoke("cust_123");

                assertThat(result.items()).hasSize(1);
                assertThat(result.items().get(0).transactionId())
                    .isEqualTo("txn_123");
            });
    }
}
```

### 3. Workflow Unit Tests

```java
public class PaymentProcessingWorkflowTest {

    @Test
    public void testWorkflowSteps() {
        var testKit = WorkflowTestKit.of(
            PaymentProcessingWorkflow::new
        );

        // Execute step 1
        testKit.step(PaymentProcessingWorkflow::chargeStep);
        assertThat(testKit.getState().status()).isEqualTo("CHARGED");

        // Execute step 2
        testKit.step(PaymentProcessingWorkflow::confirmStep);
        assertThat(testKit.getState().status()).isEqualTo("CONFIRMED");

        // Should be finished
        assertThat(testKit.isFinished()).isTrue();
    }
}
```

### 4. Endpoint Integration Tests

```java
public class PaymentEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void testPaymentEndpoint() {
        var response = httpClient
            .POST("/payments")
            .withRequestBody(new PaymentRequest(
                "cust_123",
                BigDecimal.valueOf(100),
                "USD"
            ))
            .responseBodyAs(PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().transactionId()).isNotEmpty();
    }
}
```

**Test Execution**:

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=PaymentTransactionEntityTest

# Run integration tests only
mvn verify

# Run with coverage
mvn clean test jacoco:report
```

---

## Code Organization & Patterns

### 1. Naming Conventions

| Component Type | Pattern | Example |
|---|---|---|
| Entity | `{Domain}Entity` | `PaymentTransactionEntity` |
| View | `{Domain}{Query}View` | `PaymentHistoryView` |
| Workflow | `{Process}Workflow` | `PaymentProcessingWorkflow` |
| Agent | `{Purpose}Agent` | `CustomerSupportAgent` |
| Consumer | `{Purpose}Consumer` | `EmailNotificationConsumer` |
| Timed Action | `{Domain}TimedAction` | `PaymentTimedAction` |
| Endpoint | `{Domain}Endpoint` | `PaymentEndpoint` |
| Event | `{Domain}Event` (sealed interface) | `PaymentTransactionEvent` |
| State/Record | `{Domain}` or `{Domain}State` | `PaymentTransaction` |

### 2. Immutable Update Pattern (with*)

```java
// ❌ WRONG: Using setters
state.setStatus("SUCCEEDED");
state.setAmount(newAmount);

// ✅ RIGHT: Using with* methods
var newState = state
    .withStatus("SUCCEEDED")
    .withAmount(newAmount);

// Implementation:
public record PaymentTransaction(...) {
    public PaymentTransaction withStatus(String status) {
        return new PaymentTransaction(
            transactionId, customerId, amount, currency,
            status, createdAt
        );
    }
}
```

### 3. Effect Pattern (Entity Command Handlers)

```java
// ✅ GOOD: Clear effect pattern
public Effect<PaymentResponse> charge(ChargeRequest request) {
    // 1. Validate
    if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
        return effects().error("Invalid amount");
    }

    // 2. Create event
    var event = new PaymentCharged(request.amount(), ...);

    // 3. Persist & Reply
    return effects()
        .persist(event)
        .thenReply(newState -> new PaymentResponse(newState.status()));
}
```

### 4. Builder Pattern (Complex Objects)

```java
// Request/Response records
public record PaymentRequest(
    String customerId,
    BigDecimal amount,
    String currency,
    String description
) {}

// Used in endpoints
return new PaymentRequest(
    request.getCustomerId(),
    BigDecimal.valueOf(request.getAmount()),
    request.getCurrency(),
    request.getDescription()
);
```

### 5. Switch Expression Pattern

```java
// ✅ GOOD: Modern switch with pattern matching
return switch (event) {
    case PaymentStarted ps ->
        new Payment(...);
    case PaymentSucceeded ps ->
        currentState().markSucceeded(ps.succeededAt());
    case PaymentFailed pf ->
        currentState().withStatus("FAILED");
    default -> currentState();
};
```

---

## Security & Compliance

### 1. PCI DSS Compliance

**What We Do**:
- ✅ NO raw card data stored (all tokenized via Stripe)
- ✅ NO card numbers in logs or responses
- ✅ All data encrypted in transit (HTTPS only)
- ✅ Immutable audit logs for all transactions

**Implementation**:

```java
// ✅ GOOD: Use Stripe token, not raw card
public record PaymentMethod(
    String methodId,
    String stripeTokenId,      // Not raw card number
    String last4Digits,         // Only last 4 for display
    String expiryMonth,
    String expiryYear,
    String cardholderName
) {}

// ❌ WRONG: Never store raw card data
// public record PaymentMethod(
//     String cardNumber,       // NEVER!
//     String cvv,              // NEVER!
// )
```

### 2. JWT Authentication & ACLs

```java
@HttpEndpoint
public class PaymentEndpoint {

    @Post("/payments")
    @Acl(allow = "Authenticated")  // Only authenticated users
    public PaymentResponse createPayment(PaymentRequest request) {
        // Access JWT claims
        var principal = requestContext().jwtClaims();
        var customerId = principal.subject();
        // ...
    }

    @Post("/admin/payments/{id}/cancel")
    @Acl(allow = "Authenticated", roles = {"admin"})  // Admin only
    public Done cancelPayment(String id) {
        // ...
    }

    @Get("/public/exchange-rates")
    @Acl(allow = "Public")  // Open endpoint
    public ExchangeRatesResponse getExchangeRates() {
        // ...
    }
}
```

### 3. Audit Logging

```java
@Component(id = "audit-log-entity")
public class AuditLogEntity extends KeyValueEntity<AuditLog> {
    
    public Effect<Done> logEvent(AuditEvent event) {
        var log = new AuditLog(
            UUID.randomUUID().toString(),
            event.eventType(),
            event.userId(),
            event.details(),
            Instant.now(),
            event.ipAddress()
        );

        return effects()
            .updateState(currentState().addEntry(log))
            .thenReply(Done.getInstance());
    }

    public record AuditLog(
        String id,
        String eventType,
        String userId,
        String details,
        Instant timestamp,
        String ipAddress
    ) {}
}
```

### 4. Rate Limiting

```java
@Component(id = "rate-limit")
public class RateLimitEntity extends KeyValueEntity<RateLimitState> {

    public Effect<RateLimitResponse> checkLimit(String customerId) {
        var state = currentState();

        if (state.exceededLimit(customerId)) {
            return effects()
                .reply(new RateLimitResponse(false, "Rate limit exceeded"));
        }

        var updated = state.incrementCount(customerId);
        return effects()
            .updateState(updated)
            .thenReply(new RateLimitResponse(true, "OK"));
    }
}
```

---

## Best Practices & Gotchas

### ✅ DO

- **Immutable state**: Always use `record` and `with*()` methods
- **Single responsibility**: Each component has one clear purpose
- **Fail fast**: Validate early in command handlers
- **Event sourcing for audit**: Use ESE for all financial transactions
- **Long timeouts for agents**: 60+ seconds for LLM calls
- **Limited agent retries**: Max 2 retries to avoid high costs
- **Session per conversation**: New UUID for each independent conversation
- **Type-safe responses**: Use `responseConformsTo()` for agents
- **Function tools**: Prefer over API calls for agent access to data
- **Graceful degradation**: Handle agent failures with `.onFailure()`

### ❌ DON'T

- **Mutable state**: Never modify state directly
- **Business logic in handlers**: Put it in domain objects
- **Side effects in `applyEvent()`**: Pure function only
- **Ignore errors**: Always handle failures
- **Store raw card data**: Always use Stripe tokens
- **Circular dependencies**: Follow package structure strictly
- **Multiple command handlers in agent**: One per agent only
- **Long workflows without compensation**: Always plan for failures
- **Untyped agent responses**: Use `responseConformsTo()`
- **Mix concerns**: Keep domain, application, and API separate

### 🚨 Common Gotchas

1. **Using `effect()` in step methods**
   ```java
   // ❌ WRONG
   @StepName("step1")
   private StepEffect myStep() {
       return effects()  // Wrong! Use stepEffects()
           .updateState(newState)
           .thenEnd();
   }

   // ✅ RIGHT
   @StepName("step1")
   private StepEffect myStep() {
       return stepEffects()  // Correct!
           .updateState(newState)
           .thenEnd();
   }
   ```

2. **Null in `emptyState()`**
   ```java
   // ❌ WRONG
   @Override
   public MyState emptyState() {
       return null;  // This will fail!
   }

   // ✅ RIGHT
   @Override
   public MyState emptyState() {
       return new MyState("", "", 0);  // Provide defaults
   }
   ```

3. **Async in endpoints**
   ```java
   // ❌ WRONG: Endpoints are synchronous
   public CompletionStage<Response> getPayment(String id) {
       return componentClient.forEntity()...invoke();
   }

   // ✅ RIGHT: Synchronous style
   public PaymentResponse getPayment(String id) {
       return componentClient.forEntity()...invoke();
   }
   ```

4. **ComponentClient in Entity**
   ```java
   // ❌ WRONG: Entities can't have ComponentClient
   @Component(id = "payment")
   public class PaymentEntity extends EventSourcedEntity<...> {
       public PaymentEntity(ComponentClient client) { }  // WRONG!
   }

   // ✅ RIGHT: Only in Workflows, Agents, Endpoints, Consumers
   @Component(id = "workflow")
   public class PaymentWorkflow extends Workflow<...> {
       public PaymentWorkflow(ComponentClient client) { }  // OK
   }
   ```

5. **Missing `@TypeName` on events**
   ```java
   // ❌ WRONG
   public record PaymentSucceeded(String id) { }

   // ✅ RIGHT
   @TypeName("payment.succeeded")
   public record PaymentSucceeded(String id) { }
   ```

---

## Development Workflow

### Phase-Based Development

This project follows a phase-based development approach. Each phase adds new features:

| Phase | Focus | Components |
|-------|-------|-----------|
| 1-3 | MVP Payment Processing | Entity, Workflow, Endpoint, Consumer |
| 4 | Save Payment Methods | Entity, View, Endpoint |
| 5 | Payment History | View, Endpoint |
| 6 | Refunds | Workflow, Endpoint |
| 7 | Multi-Currency | Service, Endpoint |
| 8+ | AI Agents | Agents, Orchestration |

### Step-by-Step Component Creation

**1. Design (Get approval)**
- Design domain model
- Plan state and events
- Define API contracts
- Get user approval before coding

**2. Domain Layer**
- Create domain records
- Add business logic methods
- Create event sealed interface
- Run: `mvn compile`

**3. Application Layer**
- Create Entity/Workflow/View
- Implement command/step handlers
- Subscribe to events
- Run: `mvn compile`

**4. Unit Tests**
- Write tests for components
- Mock dependencies
- Run: `mvn test`

**5. API Layer**
- Create HTTP endpoints
- Add request/response records
- Implement converters
- Run: `mvn compile`

**6. Integration Tests**
- Write end-to-end tests
- Use `TestKitSupport`
- Run: `mvn verify`

**7. Documentation**
- Update README with examples
- Add curl commands
- Document new features

### Build & Test Commands

```bash
# Compile only
mvn compile

# Run unit tests
mvn test

# Run integration tests
mvn verify

# Clean build
mvn clean install

# Check for errors
mvn clean compile

# Run specific test
mvn test -Dtest=PaymentTransactionEntityTest#testCharge

# Run with debug output
mvn test -X
```

---

## Common Problems & Solutions

### Problem 1: "Cannot find symbol: Component"

**Cause**: Missing Akka import

**Solution**:
```java
// ✅ Add this import
import akka.javasdk.annotations.Component;

@Component(id = "my-entity")
public class MyEntity extends EventSourcedEntity<...> { }
```

### Problem 2: "effect() cannot be resolved"

**Cause**: Using `effects()` in step method instead of `stepEffects()`

**Solution**:
```java
// ❌ WRONG
@StepName("step")
private StepEffect myStep() {
    return effects().updateState(...);  // effect() in step!
}

// ✅ RIGHT
@StepName("step")
private StepEffect myStep() {
    return stepEffects().updateState(...);  // stepEffects() in step
}
```

### Problem 3: "View shows empty results after event"

**Cause**: Not waiting for view to process events

**Solution**:
```java
// ❌ WRONG: Check immediately
var result = componentClient.forView().method(...).invoke();

// ✅ RIGHT: Wait for view to update
Awaitility.await()
    .atMost(10, TimeUnit.SECONDS)
    .untilAsserted(() -> {
        var result = componentClient.forView().method(...).invoke();
        assertThat(result).isNotEmpty();
    });
```

### Problem 4: "Workflow step times out"

**Cause**: Default 5s timeout too short for agent LLM calls

**Solution**:
```java
@Override
public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .stepTimeout(PaymentWorkflow::agentStep, Duration.ofSeconds(60))
        .build();
}
```

### Problem 5: "Test fails: TestModelProvider not registered"

**Cause**: Agent model not configured in test settings

**Solution**:
```java
public class MyAgentTest extends TestKitSupport {
    private final TestModelProvider agentModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withModelProvider(MyAgent.class, agentModel);  // Register here
    }
}
```

### Problem 6: "Cards with invalid expiry date still saved"

**Cause**: Validation not in domain model

**Solution**:
```java
// Add to PaymentMethod domain record
public record PaymentMethod(...) {
    public PaymentMethod {
        if (expiredMonthsAgo() > 0) {
            throw new IllegalArgumentException("Card expired");
        }
    }

    private int expiredMonthsAgo() {
        var expiryDate = YearMonth.of(
            Integer.parseInt(expiryYear),
            Integer.parseInt(expiryMonth)
        );
        return expiryDate.compareTo(YearMonth.now());
    }
}
```

### Problem 7: "Agent returns different response structure"

**Cause**: Not using type-safe response schema

**Solution**:
```java
// ❌ WRONG: No type safety
return effects()
    .systemMessage("...")
    .userMessage(query)
    .thenReply();

// ✅ RIGHT: Type-safe response
return effects()
    .systemMessage("...")
    .userMessage(query)
    .responseConformsTo(MyResponse.class)  // Enforces schema
    .onFailure(ex -> new MyResponse("error", null))  // Fallback
    .thenReply();
```

---

## Quick Reference

### Key Imports

```java
// Core framework
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.view.View;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.agent.Agent;
import akka.javasdk.client.ComponentClient;

// HTTP/gRPC
import akka.javasdk.annotations.http.*;
import akka.javasdk.grpc.AbstractGrpcEndpoint;
import akka.javasdk.http.AbstractHttpEndpoint;

// Effects
import akka.javasdk.eventsourcedentity.Effect;
import akka.javasdk.workflow.StepEffect;
import akka.Done;

// Testing
import akka.javasdk.testkit.EventSourcedTestKit;
import akka.javasdk.testkit.WorkflowTestKit;
import akka.javasdk.testkit.TestKitSupport;
```

### Common Response Patterns

```java
// Success
return effects()
    .persist(event)
    .thenReply(state -> new Response(...));

// Error
return effects()
    .error("Cannot process: reason");

// No response
return effects()
    .persist(event)
    .thenReply(Done.getInstance());

// Ignore event in consumer
return effects().ignore();

// Continue to next step
return stepEffects()
    .updateState(newState)
    .thenTransitionTo(WorkflowClass::nextStep);

// Finish workflow
return stepEffects()
    .updateState(newState)
    .thenEnd();
```

### Akka Version Check

```bash
# Check current Akka SDK version in pom.xml
grep -A 5 "akka-javasdk-parent" pom.xml

# Current version: 3.5.19
# Minimum version: 3.4
```

---

## Resources & Links

- **Akka SDK Documentation**: `akka-context/sdk/` directory
- **Agent Documentation**: `akka-context/sdk/agents.html.md`
- **Workflow Documentation**: `akka-context/sdk/workflows.html.md`
- **Project README**: `README.md`
- **Security Guidelines**: `SECURITY.md`
- **Implementation Status**: `docs/implementation/IMPLEMENTATION_SUMMARY.md`
- **AI Agents Guide**: `docs/features/AI_AGENTS.md`

---

**Document Version**: 1.0  
**Last Reviewed**: May 24, 2026  
**Maintained By**: AI Coding Assistant (GitHub Copilot)
