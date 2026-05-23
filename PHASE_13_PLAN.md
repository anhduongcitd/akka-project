# Phase 13: Agent Marketplace & Agent Chaining - Implementation Plan

## Context

The Agentic AI System (Phases 11-12) is production-ready with 6 agents, analytics, guardrails, evaluation, memory, streaming, and integrations. Phase 13 adds two critical features for scalability and composability:

1. **Agent Marketplace** - Pre-built agent templates for rapid deployment
2. **Agent Chaining** - Pipeline multiple agents for complex workflows

### Why This Matters

**Agent Marketplace**:
- **Reusability**: Deploy proven agent templates instantly
- **Consistency**: Standardized agent patterns across teams
- **Knowledge Sharing**: Community-contributed templates
- **Rapid Prototyping**: Launch new agents in minutes, not days

**Agent Chaining**:
- **Composability**: Combine simple agents into complex workflows
- **Separation of Concerns**: Each agent handles one responsibility
- **Flexibility**: Dynamic agent pipelines based on context
- **Reusability**: Same agents in different chains

### Architecture Approach

Following Akka Agent SDK best practices:
- **Marketplace**: Key-Value Entity for templates, View for browsing
- **Chaining**: Workflow orchestrating agent pipeline
- **Template Model**: JSON-based agent configuration
- **Chain DSL**: Declarative pipeline definition
- **Validation**: Template validation before deployment
- **Versioning**: Template versioning for updates

---

## Feature 1: Agent Marketplace

### Overview

A marketplace for pre-built agent templates that can be instantiated, customized, and deployed.

### Domain Model

**AgentTemplate**:
```java
record AgentTemplate(
    String templateId,
    String name,
    String description,
    String category,              // "customer-support", "fraud", "analytics", etc.
    AgentConfig config,
    List<String> tags,
    String author,
    String version,
    int downloads,
    double rating,
    Instant createdAt,
    Instant updatedAt
)
```

**AgentConfig**:
```java
record AgentConfig(
    String systemPrompt,
    List<ToolConfig> tools,
    Map<String, String> settings,
    GuardrailConfig guardrails,
    ModelConfig model
)

record ToolConfig(String toolName, Map<String, String> params)
record GuardrailConfig(List<String> enabled)
record ModelConfig(String provider, String modelId, double temperature)
```

**TemplateDeployment**:
```java
record TemplateDeployment(
    String deploymentId,
    String templateId,
    String agentId,              // Deployed agent ID
    Map<String, String> customizations,
    Instant deployedAt,
    String status                 // "active", "inactive", "failed"
)
```

### Application Layer

**AgentTemplateEntity** (Key-Value Entity):
- Entity ID: templateId
- Commands:
  * `createTemplate(AgentTemplate)` - Publish template
  * `updateTemplate(AgentConfig)` - Update configuration
  * `incrementDownloads()` - Track usage
  * `rateTemplate(double rating)` - User ratings
  * `deleteTemplate()` - Remove template

**AgentTemplateView** (View):
- Queries:
  * `getAllTemplates()` - Browse marketplace
  * `getByCategory(String category)` - Filter by category
  * `getTopRated()` - Popular templates
  * `getMostDownloaded()` - Most used
  * `searchTemplates(String query)` - Search by name/tags

**TemplateDeploymentEntity** (Key-Value Entity):
- Entity ID: deploymentId
- Commands:
  * `deployTemplate(String templateId, Map customizations)` - Deploy template as agent
  * `updateDeployment(Map customizations)` - Modify deployed agent
  * `deactivateDeployment()` - Stop deployed agent
  * `getDeploymentStatus()` - Check health

### API Layer

**AgentMarketplaceEndpoint**:
```
POST   /marketplace/templates                 - Publish template
GET    /marketplace/templates                 - List all templates
GET    /marketplace/templates/{id}            - Get template details
PUT    /marketplace/templates/{id}            - Update template
DELETE /marketplace/templates/{id}            - Delete template
GET    /marketplace/templates/category/{cat}  - Filter by category
GET    /marketplace/templates/top-rated       - Top rated templates
GET    /marketplace/templates/popular         - Most downloaded
GET    /marketplace/templates/search          - Search templates

POST   /marketplace/deploy/{templateId}       - Deploy template as agent
GET    /marketplace/deployments               - List deployments
GET    /marketplace/deployments/{id}          - Get deployment details
PUT    /marketplace/deployments/{id}          - Update deployment
DELETE /marketplace/deployments/{id}          - Deactivate deployment
```

### Web UI

**marketplace.html**:
- Template browsing with cards
- Category filters
- Search functionality
- Template details modal
- Deploy button with customization form
- Ratings and download counts
- Author information

### Pre-built Templates

**Included Templates**:
1. **Customer Support Template** - Based on CustomerSupportAgent
2. **Fraud Detection Template** - Based on FraudAnalystAgent
3. **Payment Assistant Template** - Based on PaymentAssistantAgent
4. **General Q&A Template** - Generic question answering
5. **Data Analysis Template** - Analyze structured data
6. **Email Generator Template** - Generate professional emails
7. **Content Moderator Template** - Content safety checking
8. **Transaction Analyzer Template** - Financial transaction analysis

### Tests

**AgentTemplateEntityTest** (10 tests):
- shouldCreateTemplate
- shouldUpdateTemplate
- shouldIncrementDownloads
- shouldRateTemplate
- shouldDeleteTemplate
- shouldRejectInvalidTemplate
- shouldTrackMultipleRatings
- shouldCalculateAverageRating
- shouldGetTemplateConfig
- shouldRejectDuplicateTemplate

**AgentTemplateViewIntegrationTest** (8 tests):
- shouldQueryAllTemplates
- shouldQueryByCategory
- shouldQueryTopRated
- shouldQueryMostDownloaded
- shouldSearchTemplates
- shouldReflectUpdates
- shouldHandleDelete
- shouldSortByRating

**TemplateDeploymentEntityTest** (8 tests):
- shouldDeployTemplate
- shouldUpdateDeployment
- shouldDeactivateDeployment
- shouldGetDeploymentStatus
- shouldRejectInvalidTemplate
- shouldApplyCustomizations
- shouldTrackDeploymentHistory
- shouldHandleDeploymentFailure

**AgentMarketplaceEndpointIntegrationTest** (12 tests):
- shouldPublishTemplate
- shouldGetTemplate
- shouldUpdateTemplate
- shouldDeleteTemplate
- shouldListAllTemplates
- shouldFilterByCategory
- shouldGetTopRated
- shouldGetPopular
- shouldSearchTemplates
- shouldDeployTemplate
- shouldListDeployments
- shouldDeactivateDeployment

**Total Marketplace Tests**: 38 tests

---

## Feature 2: Agent Chaining

### Overview

Pipeline multiple agents in sequence or parallel to handle complex multi-step workflows.

### Domain Model

**AgentChain**:
```java
record AgentChain(
    String chainId,
    String name,
    String description,
    List<ChainStep> steps,
    ChainConfig config,
    Instant createdAt,
    Instant updatedAt
)
```

**ChainStep**:
```java
record ChainStep(
    String stepId,
    String agentId,
    String stepType,              // "sequential", "parallel", "conditional"
    Map<String, String> inputMapping,
    Map<String, String> outputMapping,
    List<String> dependencies,     // Step IDs that must complete first
    ConditionalConfig condition    // For conditional steps
)

record ConditionalConfig(String field, String operator, String value)
```

**ChainExecution**:
```java
record ChainExecution(
    String executionId,
    String chainId,
    String sessionId,
    Map<String, Object> initialInput,
    List<StepResult> stepResults,
    String status,                 // "pending", "running", "completed", "failed"
    Instant startedAt,
    Instant completedAt
)

record StepResult(
    String stepId,
    String agentId,
    Object output,
    String status,
    long durationMs,
    Instant completedAt
)
```

### Application Layer

**AgentChainEntity** (Key-Value Entity):
- Entity ID: chainId
- Commands:
  * `createChain(AgentChain)` - Define agent pipeline
  * `updateChain(List<ChainStep>)` - Modify pipeline
  * `deleteChain()` - Remove pipeline

**AgentChainWorkflow** (Workflow):
- Orchestrates agent chain execution
- Steps:
  * `validateChainStep()` - Validate chain configuration
  * `executeSequentialStep(stepId)` - Run agent in sequence
  * `executeParallelSteps(stepIds)` - Run agents in parallel
  * `evaluateConditionStep(stepId)` - Conditional branching
  * `aggregateResultsStep()` - Combine outputs
  * `finalizeExecutionStep()` - Complete execution

**ChainExecutionEntity** (Key-Value Entity):
- Entity ID: executionId
- Commands:
  * `startExecution(String chainId, Map input)` - Start chain
  * `recordStepResult(StepResult)` - Track step completion
  * `completeExecution(Object result)` - Mark complete
  * `failExecution(String error)` - Mark failed

**AgentChainView** (View):
- Queries:
  * `getAllChains()` - List all chains
  * `getChainsByAgent(String agentId)` - Chains using agent
  * `getExecutionHistory(String chainId)` - Execution history

### API Layer

**AgentChainEndpoint**:
```
POST   /chains                     - Create agent chain
GET    /chains                     - List all chains
GET    /chains/{id}                - Get chain details
PUT    /chains/{id}                - Update chain
DELETE /chains/{id}                - Delete chain
GET    /chains/by-agent/{agentId}  - Chains using agent

POST   /chains/{id}/execute        - Execute chain
GET    /chains/executions          - List all executions
GET    /chains/executions/{id}     - Get execution details
GET    /chains/{id}/history        - Execution history
```

### Web UI

**chain-builder.html**:
- Drag-and-drop chain builder
- Visual pipeline editor
- Agent selection from marketplace
- Step configuration forms
- Input/output mapping editor
- Conditional branching UI
- Execution preview
- Real-time execution monitoring

### Pre-built Chains

**Included Chains**:
1. **Customer Support Pipeline**:
   - Step 1: CustomerSupportAgent (classify inquiry)
   - Step 2: Conditional - if refund → PaymentAssistantAgent
   - Step 3: Conditional - if fraud → FraudAnalystAgent
   - Step 4: SummarizerAgent (final response)

2. **Fraud Investigation Pipeline**:
   - Step 1: FraudAnalystAgent (analyze transaction)
   - Step 2: Parallel - CustomerSupportAgent + PaymentAssistantAgent
   - Step 3: JudgeAgent (evaluate responses)
   - Step 4: SummarizerAgent (final verdict)

3. **Payment Recovery Pipeline**:
   - Step 1: PaymentAssistantAgent (analyze failure)
   - Step 2: CustomerSupportAgent (customer communication)
   - Step 3: Conditional - if retryable → retry logic
   - Step 4: SummarizerAgent (resolution summary)

### Tests

**AgentChainEntityTest** (8 tests):
- shouldCreateChain
- shouldUpdateChain
- shouldDeleteChain
- shouldRejectInvalidChain
- shouldValidateStepDependencies
- shouldRejectCircularDependencies
- shouldGetChainConfig
- shouldRejectDuplicateStepIds

**AgentChainWorkflowTest** (12 tests):
- shouldExecuteSequentialChain
- shouldExecuteParallelSteps
- shouldHandleConditionalBranching
- shouldMapInputCorrectly
- shouldMapOutputCorrectly
- shouldHandleStepFailure
- shouldRetryFailedStep
- shouldAggregateResults
- shouldTimeoutLongRunningChain
- shouldValidateChain
- shouldHandleMissingAgent
- shouldCompleteExecution

**ChainExecutionEntityTest** (7 tests):
- shouldStartExecution
- shouldRecordStepResult
- shouldCompleteExecution
- shouldFailExecution
- shouldTrackStepDuration
- shouldGetExecutionStatus
- shouldRejectDuplicateExecution

**AgentChainEndpointIntegrationTest** (10 tests):
- shouldCreateChain
- shouldGetChain
- shouldUpdateChain
- shouldDeleteChain
- shouldListAllChains
- shouldGetChainsByAgent
- shouldExecuteChain
- shouldListExecutions
- shouldGetExecutionDetails
- shouldGetExecutionHistory

**Total Chaining Tests**: 37 tests

---

## Implementation Plan

### Phase 1: Agent Marketplace Domain & Application (3 hours)

**Files to Create**:
1. `src/main/java/com/example/payment/agents/domain/AgentTemplate.java`
2. `src/main/java/com/example/payment/agents/domain/AgentConfig.java`
3. `src/main/java/com/example/payment/agents/domain/TemplateDeployment.java`
4. `src/main/java/com/example/payment/application/AgentTemplateEntity.java`
5. `src/main/java/com/example/payment/application/AgentTemplateView.java`
6. `src/main/java/com/example/payment/application/TemplateDeploymentEntity.java`

**Steps**:
1. Create domain records with validation
2. Create AgentTemplateEntity with commands
3. Create AgentTemplateView with queries
4. Create TemplateDeploymentEntity
5. Compile and verify

### Phase 2: Agent Marketplace API & UI (2 hours)

**Files to Create**:
1. `src/main/java/com/example/payment/api/AgentMarketplaceEndpoint.java`
2. `src/main/resources/web/marketplace.html`
3. `src/main/java/com/example/payment/api/MarketplaceUIEndpoint.java`

**Steps**:
1. Create AgentMarketplaceEndpoint with 12 endpoints
2. Create marketplace.html with template browser
3. Create MarketplaceUIEndpoint to serve HTML
4. Test basic flow

### Phase 3: Agent Marketplace Tests (2 hours)

**Files to Create**:
1. `src/test/java/com/example/payment/application/AgentTemplateEntityTest.java`
2. `src/test/java/com/example/payment/application/AgentTemplateViewIntegrationTest.java`
3. `src/test/java/com/example/payment/application/TemplateDeploymentEntityTest.java`
4. `src/test/java/com/example/payment/api/AgentMarketplaceEndpointIntegrationTest.java`

**Total Marketplace Tests**: 38 tests

### Phase 4: Pre-built Templates (1 hour)

**Files to Create**:
1. `src/main/java/com/example/payment/agents/templates/TemplateLibrary.java`

**Steps**:
1. Define 8 pre-built templates
2. Populate marketplace on startup
3. Test template deployment

### Phase 5: Agent Chaining Domain & Application (3 hours)

**Files to Create**:
1. `src/main/java/com/example/payment/agents/domain/AgentChain.java`
2. `src/main/java/com/example/payment/agents/domain/ChainStep.java`
3. `src/main/java/com/example/payment/agents/domain/ChainExecution.java`
4. `src/main/java/com/example/payment/application/AgentChainEntity.java`
5. `src/main/java/com/example/payment/application/AgentChainWorkflow.java`
6. `src/main/java/com/example/payment/application/ChainExecutionEntity.java`
7. `src/main/java/com/example/payment/application/AgentChainView.java`

**Steps**:
1. Create domain records
2. Create AgentChainEntity
3. Create AgentChainWorkflow with orchestration logic
4. Create ChainExecutionEntity for tracking
5. Create AgentChainView
6. Compile and verify

### Phase 6: Agent Chaining API & UI (2 hours)

**Files to Create**:
1. `src/main/java/com/example/payment/api/AgentChainEndpoint.java`
2. `src/main/resources/web/chain-builder.html`
3. `src/main/java/com/example/payment/api/ChainBuilderUIEndpoint.java`

**Steps**:
1. Create AgentChainEndpoint with 10 endpoints
2. Create chain-builder.html with visual editor
3. Create ChainBuilderUIEndpoint to serve HTML
4. Test basic chain execution

### Phase 7: Agent Chaining Tests (3 hours)

**Files to Create**:
1. `src/test/java/com/example/payment/application/AgentChainEntityTest.java`
2. `src/test/java/com/example/payment/application/AgentChainWorkflowTest.java`
3. `src/test/java/com/example/payment/application/ChainExecutionEntityTest.java`
4. `src/test/java/com/example/payment/api/AgentChainEndpointIntegrationTest.java`

**Total Chaining Tests**: 37 tests

### Phase 8: Pre-built Chains (1 hour)

**Files to Create**:
1. `src/main/java/com/example/payment/agents/chains/ChainLibrary.java`

**Steps**:
1. Define 3 pre-built chains
2. Populate on startup
3. Test chain execution

### Phase 9: Documentation (1 hour)

**Files to Create/Update**:
1. `AGENT_MARKETPLACE.md` - Marketplace guide
2. `AGENT_CHAINING.md` - Chaining guide
3. `PHASE_13_SUMMARY.md` - Implementation summary
4. `README.md` - Update with Phase 13 features

---

## Critical Files Summary

### New Files (38 files total)

**Domain Layer (6 files)**:
- AgentTemplate.java
- AgentConfig.java
- TemplateDeployment.java
- AgentChain.java
- ChainStep.java
- ChainExecution.java

**Application Layer (7 files)**:
- AgentTemplateEntity.java
- AgentTemplateView.java
- TemplateDeploymentEntity.java
- AgentChainEntity.java
- AgentChainWorkflow.java
- ChainExecutionEntity.java
- AgentChainView.java

**API Layer (4 files)**:
- AgentMarketplaceEndpoint.java
- MarketplaceUIEndpoint.java
- AgentChainEndpoint.java
- ChainBuilderUIEndpoint.java

**Libraries (2 files)**:
- TemplateLibrary.java
- ChainLibrary.java

**Web UI (2 files)**:
- marketplace.html
- chain-builder.html

**Tests (15 files)**:
- AgentTemplateEntityTest.java
- AgentTemplateViewIntegrationTest.java
- TemplateDeploymentEntityTest.java
- AgentMarketplaceEndpointIntegrationTest.java
- AgentChainEntityTest.java
- AgentChainWorkflowTest.java
- ChainExecutionEntityTest.java
- AgentChainEndpointIntegrationTest.java
- MarketplaceUIEndpointIntegrationTest.java (bonus)
- ChainBuilderUIEndpointIntegrationTest.java (bonus)

**Documentation (2 files)**:
- AGENT_MARKETPLACE.md
- AGENT_CHAINING.md

---

## Verification

### Marketplace Verification
```bash
# Compile
mvn compile

# Run tests
mvn test -Dtest=AgentTemplateEntityTest
mvn test -Dtest=TemplateDeploymentEntityTest
mvn verify -Dtest=AgentTemplateViewIntegrationTest
mvn verify -Dtest=AgentMarketplaceEndpointIntegrationTest

# Manual testing
# 1. Access marketplace UI: http://localhost:9000/marketplace
# 2. Browse templates
# 3. Deploy template
# 4. Verify agent created
```

### Chaining Verification
```bash
# Run tests
mvn test -Dtest=AgentChainEntityTest
mvn test -Dtest=AgentChainWorkflowTest
mvn test -Dtest=ChainExecutionEntityTest
mvn verify -Dtest=AgentChainEndpointIntegrationTest

# Manual testing
# 1. Access chain builder: http://localhost:9000/chain-builder
# 2. Create chain with 2 agents
# 3. Execute chain
# 4. Verify results from both agents
```

### API Testing

**Marketplace**:
```bash
# Publish template
curl -X POST http://localhost:9000/marketplace/templates \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": "support-template-001",
    "name": "Customer Support Template",
    "category": "customer-support",
    "config": {
      "systemPrompt": "You are a helpful support agent",
      "tools": [],
      "settings": {},
      "guardrails": {"enabled": ["pii-guard"]},
      "model": {"provider": "openai", "modelId": "gpt-4o", "temperature": 0.7}
    }
  }'

# Deploy template
curl -X POST http://localhost:9000/marketplace/deploy/support-template-001 \
  -H "Content-Type: application/json" \
  -d '{
    "customizations": {
      "systemPrompt": "You are a payment support agent"
    }
  }'
```

**Chaining**:
```bash
# Create chain
curl -X POST http://localhost:9000/chains \
  -H "Content-Type: application/json" \
  -d '{
    "chainId": "support-chain-001",
    "name": "Customer Support Pipeline",
    "steps": [
      {
        "stepId": "classify",
        "agentId": "customer-support",
        "stepType": "sequential",
        "inputMapping": {"query": "$.input.query"},
        "outputMapping": {"classification": "$.output.action"}
      },
      {
        "stepId": "analyze",
        "agentId": "payment-assistant",
        "stepType": "sequential",
        "inputMapping": {"query": "$.input.query"},
        "dependencies": ["classify"]
      }
    ]
  }'

# Execute chain
curl -X POST http://localhost:9000/chains/support-chain-001/execute \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "query": "Why did my payment fail?"
    }
  }'
```

---

## Expected Test Count

**Phase 13 Total**: 75 tests
- Marketplace: 38 tests
- Chaining: 37 tests

**Project Total**: 293 (current) + 75 (Phase 13) = **368 tests**

---

## Implementation Order

1. **Marketplace Domain** → 30 min
2. **Marketplace Application** → 2 hours
3. **Marketplace API** → 1.5 hours
4. **Marketplace Web UI** → 1 hour
5. **Marketplace Tests** → 2 hours
6. **Pre-built Templates** → 1 hour

**Marketplace Subtotal**: 8 hours

7. **Chaining Domain** → 30 min
8. **Chaining Application** → 2.5 hours
9. **Chaining API** → 1.5 hours
10. **Chaining Web UI** → 1 hour
11. **Chaining Tests** → 3 hours
12. **Pre-built Chains** → 1 hour

**Chaining Subtotal**: 9.5 hours

13. **Documentation** → 1 hour

**Total Phase 13**: ~18.5 hours

**MVP** (minimum viable):
- Marketplace: Phases 1-2 (5 hours) - Template CRUD + basic UI
- Chaining: Phases 5-6 (5 hours) - Sequential chain execution

---

## Risks and Mitigations

**Risk**: Template deployment security
**Mitigation**: Validate templates, sandbox execution, rate limiting

**Risk**: Chain execution complexity
**Mitigation**: Start with sequential only, add parallel/conditional later

**Risk**: Agent chain failures
**Mitigation**: Implement retry logic, compensation steps, detailed error logging

**Risk**: Performance with long chains
**Mitigation**: Timeout per step, overall chain timeout, async execution

**Risk**: Template versioning conflicts
**Mitigation**: Semantic versioning, compatibility checks, migration guides

---

## Success Criteria

✅ **Marketplace**:
- 8+ pre-built templates available
- Template deployment < 5 seconds
- Template search and filtering works
- Ratings and download tracking accurate
- Web UI responsive and intuitive

✅ **Chaining**:
- Sequential chain execution works
- Parallel step execution (if implemented)
- Conditional branching (if implemented)
- Input/output mapping correct
- Chain execution tracking complete
- Web UI chain builder functional

✅ **Testing**:
- 75+ tests passing
- All critical paths covered
- Integration tests validate end-to-end

✅ **Documentation**:
- Marketplace guide complete
- Chaining guide complete
- API examples provided
- Web UI screenshots/videos

---

## Phase 13 Complete Deliverables

1. **Agent Marketplace** - Browse, deploy, rate templates
2. **Agent Chaining** - Pipeline agents for complex workflows
3. **8 Pre-built Templates** - Ready to deploy
4. **3 Pre-built Chains** - Example workflows
5. **2 Web UIs** - Marketplace browser + Chain builder
6. **75 Tests** - Comprehensive coverage
7. **Complete Documentation** - Guides and API docs

This positions the Agentic AI System as a **composable, scalable platform** with marketplace and chaining capabilities for enterprise deployment.
