# Agent Chaining

A powerful system for orchestrating multiple AI agents in sequential, parallel, or conditional pipelines.

## Overview

Agent Chaining enables you to:
- **Compose** multiple specialized agents into sophisticated workflows
- **Pipeline** agent outputs as inputs to subsequent agents
- **Parallelize** independent agent tasks for efficiency
- **Conditionally execute** agents based on runtime context
- **Track** execution state and step-by-step progress

## Key Concepts

### Chain Configuration
A **chain** defines the structure and execution strategy for multiple agents:
- **Execution Mode** - Sequential, Parallel, or Conditional
- **Steps** - Individual agent invocations with input/output mapping
- **Global Context** - Shared variables accessible to all steps
- **Error Handling** - Continue on error or fail fast

### Chain Execution
An **execution** represents a single run of a chain:
- **Context** - Runtime variables updated as steps complete
- **Step Results** - Captured inputs, outputs, and status per step
- **Final Output** - Aggregated result from all steps
- **Status** - PENDING, RUNNING, COMPLETED, FAILED, CANCELLED

### Template Variables
Steps use `{{variable}}` syntax for dynamic content:
```java
"Analyze transaction {{transactionId}}: {{transactionDetails}}"
```

Variables resolve from:
1. Initial context (provided at execution start)
2. Previous step outputs (stored with `outputKey`)
3. Global context (defined in chain config)

## Execution Modes

### Sequential Mode
Agents execute one after another, passing outputs as inputs.

**Use Cases:**
- Multi-stage analysis (triage → analysis → recommendation)
- Document generation (outline → draft → review → finalize)
- Decision workflows (gather info → analyze → decide → execute)

**Example:**
```
Step 1: Triage Agent
  Input: Customer query
  Output: classification ──┐
                           │
Step 2: Support Agent      │
  Input: Query + ◄─────────┘
         classification
  Output: response ─────────┐
                            │
Step 3: Escalation Agent    │
  Input: Response ◄─────────┘
  Output: final_answer
```

### Parallel Mode
All agents execute simultaneously, results aggregated at the end.

**Use Cases:**
- Multi-perspective analysis (fraud + compliance + risk)
- Parallel research (multiple sources simultaneously)
- Independent validations (syntax + security + performance)

**Example:**
```
                    ┌─→ Velocity Check Agent → velocity_result
                    │
Input ──────────────┼─→ Pattern Analysis Agent → pattern_result
(Transaction)       │
                    └─→ Risk Scoring Agent → risk_score
                              │
                              ▼
                    Aggregate Results → final_decision
```

### Conditional Mode
Agents execute based on runtime conditions.

**Use Cases:**
- Approval workflows (analyze → approve if safe → execute)
- Dynamic routing (classify → route to specialist)
- Error recovery (try primary → fallback if failed)

**Example:**
```
Step 1: Analyze
  Output: recommendation

Step 2: Execute (CONDITIONAL)
  Condition: recommendation contains "approve"
  Executes only if approved ◄───┐
                                │
Step 3: Escalate (CONDITIONAL)  │
  Condition: recommendation contains "reject"
  Executes only if rejected ────┘
```

## Pre-built Chain Templates

### 1. Customer Support Chain (Sequential)
**Steps:**
1. **Triage** (general-qa) - Classify customer inquiry
2. **Support** (customer-support) - Provide detailed assistance
3. **Escalation** (summarizer) - Summarize if escalation needed

**Use Case:** Automated customer support with intelligent triage

```bash
curl -X POST http://localhost:9000/agent-chains/chain-customer-support-v1/execute \
  -H "Content-Type: application/json" \
  -d '{
    "initialContext": {
      "query": "I need a refund for transaction txn_abc123"
    }
  }'
```

### 2. Fraud Detection Chain (Parallel)
**Steps:**
1. **Velocity Check** (fraud-analyst) - Analyze transaction frequency
2. **Pattern Analysis** (fraud-analyst) - Check spending patterns
3. **Risk Scoring** (fraud-analyst) - Calculate risk score

**Use Case:** Comprehensive fraud detection with multiple signals

All checks run simultaneously, results combined for final decision.

### 3. Payment Analysis Chain (Sequential)
**Steps:**
1. **Transaction Review** (transaction-analyzer) - Review transaction details
2. **Failure Analysis** (payment-assistant) - Diagnose failure
3. **Recovery Plan** (payment-assistant) - Recommend recovery actions

**Use Case:** Payment failure troubleshooting and recovery

### 4. Content Moderation Chain (Parallel)
**Steps:**
1. **Toxic Language Check** (content-moderator)
2. **PII Detection** (content-moderator)
3. **Spam Detection** (content-moderator)

**Use Case:** Comprehensive content safety checks

### 5. Research Chain (Sequential)
**Steps:**
1. **Planning** (planner) - Create research plan
2. **Data Analysis** (data-analyst) - Execute research
3. **Synthesis** (summarizer) - Synthesize findings

**Use Case:** Complex research tasks requiring planning

### 6. Decision Making Chain (Conditional)
**Steps:**
1. **Analyze** (data-analyst) - Analyze decision factors
2. **Recommend** (general-qa) - Generate recommendation
3. **Execute** (customer-support) - Execute if approved

**Use Case:** Automated decision workflows with approval gates

### 7. Email Response Chain (Sequential)
**Steps:**
1. **Draft** (email-generator) - Generate email draft
2. **Review** (content-moderator) - Check tone and compliance
3. **Finalize** (email-generator) - Apply review feedback

**Use Case:** Quality-controlled email generation

### 8. Transaction Audit Chain (Sequential)
**Steps:**
1. **Review** (transaction-analyzer) - Review transaction
2. **Compliance Check** (fraud-analyst) - Verify compliance
3. **Audit Report** (summarizer) - Generate report

**Use Case:** Transaction auditing and compliance

## API Reference

### Chain Management

**Create Chain**
```bash
POST /agent-chains/
Content-Type: application/json

{
  "chainId": "my-custom-chain",
  "name": "My Custom Chain",
  "description": "Custom workflow",
  "executionMode": "SEQUENTIAL",
  "steps": [
    {
      "stepId": "step1",
      "agentId": "agent-1",
      "inputTemplate": "Process: {{input}}",
      "parameters": {},
      "outputKey": "step1_result"
    },
    {
      "stepId": "step2",
      "agentId": "agent-2",
      "inputTemplate": "Review: {{step1_result}}",
      "parameters": {},
      "outputKey": "final_result"
    }
  ],
  "globalContext": {
    "environment": "production"
  },
  "continueOnError": false
}
```

**Get Chain**
```bash
GET /agent-chains/{chainId}
```

**Update Chain**
```bash
PUT /agent-chains/{chainId}
Content-Type: application/json

{
  "name": "Updated Name",
  "description": "Updated description",
  ...
}
```

**Delete Chain**
```bash
DELETE /agent-chains/{chainId}
```

**List All Chains**
```bash
GET /agent-chains/
```

**Search Chains**
```bash
GET /agent-chains/search?query=fraud
```

### Chain Execution

**Execute Chain**
```bash
POST /agent-chains/{chainId}/execute
Content-Type: application/json

{
  "initialContext": {
    "transactionId": "txn_123",
    "amount": "500.00",
    "customerId": "cust_456"
  }
}
```

Response:
```json
{
  "executionId": "exec-abc123",
  "chainId": "chain-fraud-detection-v1",
  "status": "RUNNING",
  "message": null
}
```

**Get Execution Status**
```bash
GET /agent-chains/executions/{executionId}
```

Response:
```json
{
  "executionId": "exec-abc123",
  "chainId": "chain-fraud-detection-v1",
  "status": "COMPLETED",
  "stepResults": [
    {
      "stepId": "velocity-check",
      "agentId": "fraud-analyst",
      "status": "COMPLETED",
      "output": "No velocity violations detected",
      "durationMs": 1234,
      "errorMessage": null
    },
    {
      "stepId": "pattern-analysis",
      "agentId": "fraud-analyst",
      "status": "COMPLETED",
      "output": "Spending pattern normal",
      "durationMs": 1456,
      "errorMessage": null
    }
  ],
  "finalOutput": "Transaction approved - no fraud indicators",
  "durationMs": 2890,
  "startedAt": "2026-05-23T10:00:00Z",
  "completedAt": "2026-05-23T10:00:03Z",
  "errorMessage": null
}
```

**Cancel Execution**
```bash
POST /agent-chains/executions/{executionId}/cancel
```

**Get Chain Executions**
```bash
GET /agent-chains/{chainId}/executions
```

**Get Recent Executions**
```bash
GET /agent-chains/executions
```

**Get Running Executions**
```bash
GET /agent-chains/executions/running
```

**Get Failed Executions**
```bash
GET /agent-chains/executions/failed
```

## Domain Model

### AgentChainConfig
```java
public record AgentChainConfig(
    String chainId,
    String name,
    String description,
    ExecutionMode executionMode,  // SEQUENTIAL, PARALLEL, CONDITIONAL
    List<ChainStep> steps,
    Map<String, String> globalContext,
    boolean continueOnError
)
```

### ChainStep
```java
public record ChainStep(
    String stepId,
    String agentId,
    String inputTemplate,      // "Analyze: {{input}}"
    Map<String, String> parameters,
    String outputKey,          // Variable name for output
    Condition condition        // Optional execution condition
)
```

### Condition
```java
public record Condition(
    String contextKey,         // Variable to check
    ConditionOperator operator, // EQUALS, CONTAINS, EXISTS, etc.
    String expectedValue       // Expected value for comparison
)
```

### AgentChainExecution
```java
public record AgentChainExecution(
    String executionId,
    String chainId,
    ExecutionStatus status,    // PENDING, RUNNING, COMPLETED, FAILED
    Map<String, Object> context,
    List<StepResult> stepResults,
    String finalOutput,
    Instant startedAt,
    Instant completedAt,
    String errorMessage
)
```

## Workflow Execution

Chains execute via `AgentChainWorkflow`:

1. **Load Configuration** - Fetch chain config from entity
2. **Initialize Context** - Set initial variables
3. **Route Execution** - Choose sequential/parallel/conditional strategy
4. **Execute Steps** - Invoke agents with rendered inputs
5. **Update Context** - Store step outputs as variables
6. **Finalize** - Aggregate results and complete

### Sequential Execution
```java
Step 1 → Update Context → Step 2 → Update Context → Step 3 → Finalize
```

### Parallel Execution
```java
┌─ Step 1 ─┐
├─ Step 2 ─┤ → Aggregate → Finalize
└─ Step 3 ─┘
```

### Error Handling
- **Continue on error**: Skip failed step, proceed to next
- **Fail fast**: Stop execution on first error

## Building Custom Chains

### Example: Multi-Agent Analysis

```java
var config = new AgentChainConfig(
    "analysis-chain",
    "Multi-Agent Analysis",
    "Comprehensive analysis with multiple agents",
    AgentChainConfig.ExecutionMode.SEQUENTIAL,
    List.of(
        // Step 1: Initial review
        new AgentChainConfig.ChainStep(
            "review",
            "transaction-analyzer",
            "Review transaction {{transactionId}}: {{details}}",
            Map.of(),
            "review_result"
        ),
        // Step 2: Risk assessment
        new AgentChainConfig.ChainStep(
            "risk",
            "fraud-analyst",
            "Assess risk based on: {{review_result}}",
            Map.of(),
            "risk_assessment"
        ),
        // Step 3: Final recommendation
        new AgentChainConfig.ChainStep(
            "recommend",
            "summarizer",
            "Recommend action:\nReview: {{review_result}}\nRisk: {{risk_assessment}}",
            Map.of(),
            "recommendation"
        )
    ),
    Map.of("priority", "high"),
    false // Stop on error
);
```

### Example: Conditional Execution

```java
var condition = new AgentChainConfig.Condition(
    "risk_level",
    AgentChainConfig.Condition.ConditionOperator.EQUALS,
    "HIGH"
);

var step = new AgentChainConfig.ChainStep(
    "escalate",
    "escalation-agent",
    "Escalate high-risk transaction: {{transactionId}}",
    Map.of(),
    "escalation_result",
    condition  // Only executes if risk_level == HIGH
);
```

## Best Practices

### Chain Design
1. **Single Responsibility** - Each step does one thing well
2. **Clear Outputs** - Use descriptive output keys
3. **Template Clarity** - Write readable input templates
4. **Error Handling** - Choose appropriate failure strategy
5. **Context Management** - Keep context variables organized

### Performance
1. **Parallel Execution** - Use for independent tasks
2. **Agent Selection** - Choose specialized agents per step
3. **Timeout Configuration** - Set appropriate step timeouts (60s for LLMs)
4. **Retry Strategy** - Limit retries to avoid excessive costs

### Testing
1. **Unit Test Steps** - Test individual agent invocations
2. **Integration Test Chains** - Test full chain execution
3. **Mock Agents** - Use TestModelProvider for deterministic tests
4. **Context Validation** - Verify context updates at each step

### Production
1. **Monitoring** - Track execution success rates
2. **Logging** - Audit all chain executions
3. **Rate Limiting** - Prevent excessive agent calls
4. **Cost Tracking** - Monitor LLM token usage per chain

## Advanced Features

### Dynamic Agent Selection
Agents called by ID at runtime - supports dynamic routing:

```java
String agentId = determineAgentBasedOnContext(context);
componentClient
    .forAgent()
    .inSession(sessionId)
    .dynamicCall(agentId)  // Runtime agent selection
    .invoke(input);
```

### Session Memory
All agents in a chain share session memory when using same session ID:

```java
String sessionId = commandContext().workflowId();
```

Agents in the chain can reference conversation history from previous steps.

### Condition Operators
- `EQUALS` - Exact match
- `NOT_EQUALS` - Inverse match
- `CONTAINS` - Substring match
- `NOT_CONTAINS` - Substring absence
- `EXISTS` - Variable exists
- `NOT_EXISTS` - Variable missing

### Template Rendering
Variables resolve at runtime:
```java
String template = "Analyze {{type}} for customer {{customerId}}";
Map<String, Object> context = Map.of(
    "type", "payment",
    "customerId", "cust_123"
);
// Renders to: "Analyze payment for customer cust_123"
```

## Views

### AgentChainView
Query chains by:
- `getAllChains()` - All configured chains
- `getById(chainId)` - Single chain
- `getByExecutionMode(mode)` - Sequential/Parallel/Conditional chains
- `searchChains(query)` - Search by name

### AgentChainExecutionView
Query executions by:
- `getAllExecutions()` - All executions
- `getById(executionId)` - Single execution with full details
- `getByChainId(chainId)` - Executions for specific chain
- `getByStatus(status)` - Filter by status
- `getRecent(limit)` - Recent executions
- `getRunningExecutions()` - Currently executing
- `getFailedExecutions()` - Failed executions

## Troubleshooting

### Chain Not Executing
**Symptom:** Execution stuck in RUNNING status

**Causes:**
- Agent timeout (default 60s)
- Invalid agent ID
- Missing context variables

**Solution:**
```bash
# Check execution status
curl http://localhost:9000/agent-chains/executions/{executionId}

# Look for error in stepResults
# Cancel if stuck
curl -X POST http://localhost:9000/agent-chains/executions/{executionId}/cancel
```

### Context Variable Missing
**Symptom:** Error "Variable {{variable}} not found in context"

**Causes:**
- Typo in variable name
- Previous step didn't set output
- Step skipped due to condition

**Solution:**
- Verify output keys match input templates
- Check condition logic
- Review step results for actual outputs

### Step Failed
**Symptom:** Step status = FAILED

**Causes:**
- Agent error (invalid input, tool failure, LLM error)
- Timeout exceeded
- Guardrail violation

**Solution:**
```bash
# Check step error message
curl http://localhost:9000/agent-chains/executions/{executionId}

# Review errorMessage in stepResults
# Fix agent input template or configuration
```

## Examples

### Sequential Chain with Error Handling
```bash
curl -X POST http://localhost:9000/agent-chains/ \
  -H "Content-Type: application/json" \
  -d '{
    "chainId": "robust-analysis",
    "name": "Robust Analysis Chain",
    "executionMode": "SEQUENTIAL",
    "steps": [
      {
        "stepId": "parse",
        "agentId": "parser",
        "inputTemplate": "Parse: {{rawData}}",
        "outputKey": "parsed"
      },
      {
        "stepId": "validate",
        "agentId": "validator",
        "inputTemplate": "Validate: {{parsed}}",
        "outputKey": "validated"
      },
      {
        "stepId": "analyze",
        "agentId": "analyzer",
        "inputTemplate": "Analyze: {{validated}}",
        "outputKey": "analysis"
      }
    ],
    "continueOnError": true  # Continue even if step fails
  }'
```

### Parallel Chain for Comprehensive Analysis
```bash
curl -X POST http://localhost:9000/agent-chains/chain-fraud-detection-v1/execute \
  -H "Content-Type: application/json" \
  -d '{
    "initialContext": {
      "customerId": "cust_789",
      "transactionId": "txn_456",
      "transactionDetails": "Amount: $5000, Merchant: Electronics Store"
    }
  }'

# All checks run simultaneously:
# - Velocity check (payment frequency)
# - Pattern analysis (spending habits)
# - Risk scoring (transaction risk)
```

### Conditional Chain with Approval Gate
```bash
curl -X POST http://localhost:9000/agent-chains/chain-decision-making-v1/execute \
  -H "Content-Type: application/json" \
  -d '{
    "initialContext": {
      "decisionContext": "Refund request for $250, customer has good history"
    }
  }'

# Flow:
# 1. Analyze decision factors
# 2. Generate recommendation
# 3. Execute ONLY if recommendation contains "approve"
```

## Resources

- [AI Agents Guide](AI_AGENTS.md)
- [Agent Marketplace](AGENT_MARKETPLACE.md)
- [Akka Workflow Documentation](https://doc.akka.io/workflows)
- [Chain Templates Source](src/main/java/com/example/payment/application/ChainTemplateLibrary.java)

## Support

For issues with:
- **Chain configuration** - Check domain model and validation
- **Execution failures** - Review step error messages
- **Performance** - Consider parallel execution
- **Custom chains** - Consult best practices guide
