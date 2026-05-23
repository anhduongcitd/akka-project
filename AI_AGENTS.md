# AI Agent System Documentation

## Overview

The Online Payment Service includes an **Agentic AI System** that provides intelligent automation for customer support, fraud detection, and payment failure resolution. The system uses LLM-powered agents with guardrails, function tools, and orchestration workflows.

**Status**: ✅ Production-Ready (103 tests passing)

## Architecture

### Agent Components

**3 Specialized Agents:**
- **CustomerSupportAgent** - 24/7 conversational support for payment inquiries
- **FraudAnalystAgent** - ML-enhanced fraud detection with confidence scoring
- **PaymentAssistantAgent** - Intelligent payment failure analysis and recovery

**Orchestration:**
- **AgentOrchestrationWorkflow** - Supervisor pattern for multi-agent coordination
- Session memory shared across agents via session ID
- 60-second timeouts for LLM calls
- Max 2 retries with failover to error handling

**Security:**
- **3 Guardrails** - PII detection, output validation, audit logging
- Runtime-enforced via Akka SDK guardrail framework
- All agent interactions logged for compliance

### Technology Stack

- **Akka SDK 3.4+** - Agent framework with built-in LLM integration
- **9router** - LLM provider (OpenAI-compatible API)
- **Function Tools** - Agents access payment entities/views directly
- **Structured Responses** - Type-safe JSON responses via `responseConformsTo()`

## Agent Capabilities

### 1. Customer Support Agent

**Component ID**: `customer-support`

**Purpose**: Self-service payment support chatbot

**Capabilities:**
- Answer payment status questions
- Check refund eligibility
- Trace transaction history
- Escalate complex issues to human support

**Function Tools:**
- `getPaymentHistory(customerId)` - Query PaymentHistoryView
- `getTransactionDetails(transactionId)` - Query PaymentTransactionEntity
- `checkRefundEligibility(transactionId)` - Validate refund status

**Response Structure:**
```java
public record SupportResponse(
    String answer,              // Human-readable response
    String action,              // INFORM, REFUND_ELIGIBLE, ESCALATE
    String confidence,          // HIGH, MEDIUM, LOW
    String transactionId,       // Transaction being discussed
    RefundRecommendation refund // Refund details if eligible
)
```

**Example Usage:**
```bash
curl -X POST http://localhost:9000/agents/support/query \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_123",
    "query": "Where is my refund for transaction txn_abc?",
    "sessionId": null
  }'
```

Response:
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "answer": "Your refund of $25.00 was processed 2 days ago and should appear in your account within 5-7 business days.",
  "action": "INFORM",
  "confidence": "HIGH",
  "transactionId": "txn_abc",
  "refund": null
}
```

**Session Continuity:**
- Include `sessionId` from previous response to maintain conversation context
- Agents share memory within same session
- Generate new session ID for new conversations

### 2. Fraud Analyst Agent

**Component ID**: `fraud-analyst`

**Purpose**: ML-enhanced fraud detection beyond rule-based checks

**Capabilities:**
- Analyze transaction patterns for fraud indicators
- Provide risk scores with confidence levels
- Learn from customer spending behavior
- Flag suspicious patterns (velocity, high-value, duplicates)

**Function Tools:**
- `getSpendingPattern(customerId)` - Analyze historical spending
- `getFraudHistory(customerId)` - Query past fraud alerts from AuditLogEntity
- `analyzeTransactionAmount(customerId, amount)` - Compare to baseline

**Response Structure:**
```java
public record FraudAnalysis(
    boolean isSuspicious,
    String riskLevel,           // LOW, MEDIUM, HIGH, CRITICAL
    double confidenceScore,     // 0.0-1.0
    List<String> riskFactors,   // Specific patterns detected
    String recommendation,      // APPROVE, REVIEW, DECLINE
    String reasoning            // Detailed explanation
)
```

**Risk Levels:**
- **LOW**: Normal transaction, consistent with patterns
- **MEDIUM**: Some unusual factors, could be legitimate
- **HIGH**: Multiple red flags, manual review needed
- **CRITICAL**: Clear fraud indicators, block immediately

**Example Usage:**
```bash
curl -X POST http://localhost:9000/agents/fraud/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_456",
    "transactionId": "txn_xyz",
    "amount": "5000.00",
    "currency": "USD",
    "merchantReference": "ORDER-456"
  }'
```

Response:
```json
{
  "sessionId": "660e8400-e29b-41d4-a716-446655440001",
  "isSuspicious": true,
  "riskLevel": "HIGH",
  "confidenceScore": 0.85,
  "riskFactors": [
    "Amount 10x higher than customer average",
    "First transaction from new location",
    "Velocity limit approaching"
  ],
  "recommendation": "REVIEW",
  "reasoning": "Multiple fraud indicators detected. Transaction amount significantly exceeds customer's normal spending pattern. Manual review recommended before approving."
}
```

### 3. Payment Assistant Agent

**Component ID**: `payment-assistant`

**Purpose**: Intelligent payment failure resolution

**Capabilities:**
- Analyze payment failures and determine root cause
- Suggest recovery actions (retry, change card, contact bank)
- Check for alternative payment methods
- Provide user-friendly failure explanations

**Function Tools:**
- `getTransactionFailureDetails(transactionId)` - Get failure reason from PaymentTransactionEntity
- `checkPaymentMethod(paymentMethodId)` - Check card expiration status
- `getAlternativeMethods(customerId)` - Find other saved cards via CustomerPaymentMethodsView

**Response Structure:**
```java
public record FailureAnalysis(
    String failureType,         // TRANSIENT, CARD_EXPIRED, INSUFFICIENT_FUNDS, GATEWAY_ERROR, FRAUD_BLOCK
    String severity,            // TEMPORARY, RECOVERABLE, TERMINAL
    List<RecoveryAction> actions,
    String customerMessage      // User-friendly explanation
)

public record RecoveryAction(
    String action,              // RETRY, CHANGE_CARD, CONTACT_BANK, UPDATE_CARD, DISPUTE
    String description,
    int priority                // 1=highest
)
```

**Failure Types:**
- **TRANSIENT**: Temporary network/gateway issue, retry likely works
- **CARD_EXPIRED**: Card expiration date passed
- **INSUFFICIENT_FUNDS**: Issuer declined due to low balance
- **GATEWAY_ERROR**: Payment gateway unavailable
- **FRAUD_BLOCK**: Blocked by fraud detection system

**Example Usage:**
```bash
curl -X POST http://localhost:9000/agents/failures/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_789",
    "transactionId": "txn_failed"
  }'
```

Response:
```json
{
  "sessionId": "770e8400-e29b-41d4-a716-446655440002",
  "failureType": "CARD_EXPIRED",
  "severity": "RECOVERABLE",
  "actions": [
    {
      "action": "UPDATE_CARD",
      "description": "Update your card expiration date in payment settings",
      "priority": 1
    },
    {
      "action": "CHANGE_CARD",
      "description": "Use a different payment method from your saved cards",
      "priority": 2
    }
  ],
  "customerMessage": "Your card has expired. Please update your card information or use a different payment method to complete your purchase."
}
```

## Guardrails

### 1. PII Detection Guardrail

**Class**: `PaymentPIIGuard`

**Purpose**: Prevent exposure of sensitive payment data

**Detects:**
- Credit card numbers (various formats)
- CVV/CVC codes
- Social Security Numbers
- Email addresses (context-aware)

**Configuration:**
```hocon
"pii-guard" {
  class = "com.example.payment.agents.guardrails.PaymentPIIGuard"
  agents = ["customer-support", "fraud-analyst", "payment-assistant"]
  category = PII
  use-for = ["model-request", "model-response"]
  report-only = false  # Abort if PII detected
}
```

**Behavior**: Blocks execution if PII detected in model request or response

### 2. Output Validation Guardrail

**Class**: `OutputValidationGuard`

**Purpose**: Ensure agent outputs are valid and safe

**Validates:**
- Refund amounts don't exceed transaction amounts
- Action codes are from approved list
- Transaction IDs are valid format
- No unauthorized operations (delete, modify, etc.)

**Configuration:**
```hocon
"output-validation" {
  class = "com.example.payment.agents.guardrails.OutputValidationGuard"
  agents = ["customer-support", "payment-assistant"]
  category = POLICY
  use-for = ["model-response"]
  report-only = false  # Abort invalid outputs
}
```

**Behavior**: Blocks execution if model response contains invalid data or forbidden operations

### 3. Audit Logger Guardrail

**Class**: `AuditLoggerGuard`

**Purpose**: Log all agent interactions for compliance

**Logs:**
- Agent ID, session ID, timestamp
- Input prompt (sanitized)
- Output response (sanitized)
- Guardrail violations

**Configuration:**
```hocon
"audit-logger" {
  class = "com.example.payment.agents.guardrails.AuditLoggerGuard"
  agents = ["customer-support", "fraud-analyst", "payment-assistant"]
  category = AUDIT
  use-for = ["model-request", "model-response"]
  report-only = true  # Log only, never blocks
}
```

**Behavior**: Always allows execution, logs all interactions for compliance (PCI DSS Requirement 10)

## Configuration

### LLM Provider Setup (9router)

**File**: `src/main/resources/application.conf`

```hocon
akka.javasdk.agent {
  openai {
    base-url = ${?LLM_BASE_URL}   # e.g., https://9router.ai/v1
    api-key = ${?LLM_API_KEY}
    model-id = ${?LLM_MODEL_ID}   # e.g., "gpt-4o", "claude-sonnet-4"
  }
}
```

### Environment Variables

Required:
```bash
export LLM_BASE_URL="https://9router.ai/v1"
export LLM_API_KEY="your-9router-api-key"
export LLM_MODEL_ID="gpt-4o"  # or "claude-sonnet-4"
```

**Supported Models:**
- OpenAI: `gpt-4o`, `gpt-4-turbo`, `gpt-3.5-turbo`
- Anthropic: `claude-sonnet-4`, `claude-opus-4`
- Any OpenAI-compatible API endpoint

### Model Selection Strategy

- **Customer Support**: Balance between speed and quality (gpt-4o recommended)
- **Fraud Detection**: High accuracy critical (claude-sonnet-4 recommended)
- **Failure Analysis**: Fast recovery needed (gpt-4-turbo recommended)

Override per-agent in code if needed:
```java
return effects()
    .systemMessage(systemPrompt)
    .userMessage(userMessage)
    .model(ModelProvider.openAi().withModelId("claude-sonnet-4"))
    .responseConformsTo(FraudAnalysis.class)
    .thenReply();
```

## Testing

### Unit Tests with TestModelProvider

```java
public class CustomerSupportAgentTest extends TestKitSupport {
    private final TestModelProvider supportModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.api-key = test")
            .withModelProvider(CustomerSupportAgent.class, supportModel);
    }

    @Test
    public void shouldHandleQuery() {
        var expectedResponse = new CustomerSupportAgent.SupportResponse(
            "Your payment was successful",
            "INFORM",
            "HIGH",
            "txn_123",
            null
        );

        supportModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var result = componentClient
            .forAgent()
            .inSession("test-session")
            .method(CustomerSupportAgent::handleQuery)
            .invoke(new SupportRequest("cust_123", "test"));

        assertThat(result.action()).isEqualTo("INFORM");
    }
}
```

### Integration Tests

```java
public class AgentEndpointIntegrationTest extends TestKitSupport {
    @Test
    public void shouldCallAgentViaEndpoint() {
        var request = new AgentEndpoint.SupportQueryRequest(
            "cust_123",
            "Where is my refund?",
            null
        );

        var response = httpClient
            .POST("/agents/support/query")
            .withRequestBody(request)
            .responseBodyAs(AgentEndpoint.SupportQueryResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().sessionId()).isNotNull();
    }
}
```

### Test Coverage

- **12 agent unit tests** - Agent configuration, structured responses, fallbacks
- **7 agent integration tests** - HTTP endpoint integration, session management
- **103 total tests passing** (including existing payment tests)

## Production Deployment

### Pre-Deployment Checklist

**Configuration:**
- [ ] Set `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL_ID` environment variables
- [ ] Verify 9router API key has sufficient quota
- [ ] Test model selection for each agent type
- [ ] Configure guardrails for production use

**Security:**
- [ ] PII guardrail enabled and tested
- [ ] Output validation guardrail enabled
- [ ] Audit logging configured with retention policy
- [ ] JWT authentication enabled for agent endpoints

**Monitoring:**
- [ ] Agent interaction metrics tracked
- [ ] Guardrail violation alerts configured
- [ ] LLM token usage monitored
- [ ] Response latency dashboards created

**Cost Management:**
- [ ] Set token limits per session
- [ ] Configure rate limiting for agent endpoints
- [ ] Monitor daily LLM API costs
- [ ] Implement caching for common queries

### Deployment Commands

```bash
# Build
mvn clean install -DskipTests

# Deploy to Akka Platform
akka service deploy online-payment-service online-payment-service:1.0-SNAPSHOT --push

# Set environment variables
akka service env set online-payment-service \
  LLM_BASE_URL=$LLM_BASE_URL \
  LLM_API_KEY=$LLM_API_KEY \
  LLM_MODEL_ID=$LLM_MODEL_ID
```

### Monitoring Agent Performance

**Key Metrics:**
- Agent invocation count (by agent type)
- Average response time (P50, P95, P99)
- Guardrail violation rate
- Token consumption per interaction
- Escalation rate (support agent)
- Fraud detection accuracy (fraud analyst)

**Logs to Monitor:**
- Guardrail audit logs (all interactions)
- Agent failures and fallbacks
- Function tool invocation errors
- Model timeout/retry events

## Cost Considerations

### Token Usage Estimates

**Per Interaction:**
- Customer Support: 1,000-3,000 tokens (system prompt + user query + response)
- Fraud Analysis: 800-2,000 tokens (shorter prompts, structured output)
- Failure Analysis: 600-1,500 tokens (focused analysis)

**Function Tool Overhead:**
- Each tool call: +200-500 tokens (tool descriptions in context)
- Agents use 3 tools each, but only call when needed

### Cost Optimization

1. **Cache Common Queries**
   - Store frequent support questions/answers
   - Reduce redundant LLM calls by 30-40%

2. **Use Smaller Models for Simple Tasks**
   - gpt-3.5-turbo for straightforward support queries
   - Save gpt-4o/claude for complex analysis

3. **Limit Context Window**
   - Prune old messages from session memory
   - Keep last 5-10 interactions max

4. **Batch Processing**
   - Process multiple fraud checks in batch
   - Amortize token costs across transactions

## Troubleshooting

### Agent Returns Fallback Response

**Symptom**: Agent always returns ESCALATE or default response

**Causes:**
1. LLM API credentials invalid
2. Model not available (quota exceeded)
3. Function tool execution failing
4. Guardrail blocking execution

**Solution:**
```bash
# Check API key
curl -X POST $LLM_BASE_URL/chat/completions \
  -H "Authorization: Bearer $LLM_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"$LLM_MODEL_ID","messages":[{"role":"user","content":"test"}]}'

# Check agent logs
akka service logs online-payment-service | grep "Agent\|Guardrail"
```

### Guardrail False Positives

**Symptom**: PII guardrail blocks legitimate responses

**Solution**: Adjust regex patterns in `PaymentPIIGuard.java` or add exceptions:
```java
// Allow test emails
if (email.contains("example.com") || email.contains("test.com")) {
    return Result.OK;
}
```

### High Latency

**Symptom**: Agent responses take >10 seconds

**Causes:**
1. Model response time slow
2. Multiple function tool calls
3. Large context window

**Solution:**
- Use faster model (gpt-4-turbo instead of gpt-4o)
- Reduce context window size
- Cache tool responses
- Increase timeout to 90s for complex queries

## Future Enhancements

### Planned Features

1. **Dynamic Agent Team** (Phase 3.5)
   - PlannerAgent selects agents dynamically
   - Multi-agent collaboration on complex issues
   - See plan: `AgentOrchestrationWorkflow` with dynamic routing

2. **Advanced Guardrails**
   - Jailbreak detection with `SimilarityGuard`
   - Toxic language filtering
   - Hallucination detection

3. **Agent Analytics Dashboard**
   - Real-time agent performance metrics
   - Cost tracking per agent
   - Escalation trend analysis

4. **Fine-Tuned Models**
   - Train custom models on payment domain
   - Improve fraud detection accuracy
   - Reduce token costs

## References

- [Akka Agent SDK Documentation](https://doc.akka.io/java/agents.html)
- [Guardrails Reference](https://doc.akka.io/java/agents/guardrails.html)
- [9router API Documentation](https://9router.ai/docs)
- [Project README](README.md)

---

**Built with Akka SDK** | **Production-Ready** | **103 Tests Passing**
