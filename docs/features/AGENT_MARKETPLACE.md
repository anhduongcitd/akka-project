# Agent Marketplace

A comprehensive marketplace system for discovering, sharing, and deploying pre-built AI agent templates.

## Overview

The Agent Marketplace provides a centralized platform for:
- **Discovering** pre-built agent templates for common use cases
- **Rating** and reviewing agent templates
- **Deploying** templates with custom configurations
- **Tracking** usage and popularity metrics

## Features

### 🏪 Template Discovery
- Browse all available templates
- Filter by category (customer-support, fraud-detection, analytics, etc.)
- Search by name, description, or tags
- Sort by rating, downloads, or popularity

### ⭐ Rating System
- 5-star rating system (0.0 - 5.0)
- Average rating calculation
- Rating count tracking
- Top-rated template queries

### 📊 Analytics
- Download tracking
- Popularity scoring (weighted rating + downloads)
- Usage metrics per template
- Category-based analytics

### 🚀 Template Deployment
- One-click deployment from marketplace
- Custom configuration override
- Deployment lifecycle management (active, inactive, failed)
- Deployment history tracking

## Pre-built Templates

### 1. Customer Support Agent
**Category:** customer-support  
**Tools:** PaymentHistoryView, PaymentTransactionEntity, RefundToolkit  
**Use Case:** 24/7 self-service payment inquiries, refund requests, account management

```bash
curl -X POST http://localhost:9000/marketplace/deploy/template-customer-support-v1 \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "my-support-agent",
    "customizations": {
      "maxConversationTurns": "15",
      "responseStyle": "friendly"
    }
  }'
```

### 2. Fraud Detection Analyst
**Category:** fraud-detection  
**Tools:** PaymentHistoryView, FraudCheckEntity, AuditLogEntity  
**Use Case:** ML-enhanced fraud detection with confidence scoring

**Configuration:**
- Lower temperature (0.3) for consistency
- Real-time velocity and pattern analysis
- Risk scoring with explanations

### 3. Payment Failure Assistant
**Category:** payment-support  
**Tools:** PaymentTransactionEntity, PaymentMethodEntity, CustomerPaymentMethodsView  
**Use Case:** Intelligent payment failure analysis and recovery recommendations

### 4. General Q&A Assistant
**Category:** general  
**Tools:** None (knowledge-based)  
**Use Case:** Versatile assistant for payment service information and guidance

### 5. Data Analysis Specialist
**Category:** analytics  
**Tools:** PaymentHistoryView, AgentAnalyticsView  
**Use Case:** Advanced analytics and insights for transaction data

**Configuration:**
- Lower temperature (0.2) for analysis accuracy
- Statistical summaries and trend analysis

### 6. Email Composition Assistant
**Category:** communication  
**Tools:** PaymentTransactionEntity  
**Use Case:** AI-powered email generation for payment communications

**Guardrails:** PII detection, toxic language filtering, output validation

### 7. Content Moderation Specialist
**Category:** moderation  
**Tools:** None (policy-based)  
**Use Case:** Automated content review and policy enforcement

**Configuration:**
- Very low temperature (0.1) for consistency
- Toxic language, spam, and security threat detection

### 8. Transaction Analysis Expert
**Category:** analytics  
**Tools:** PaymentTransactionEntity, PaymentHistoryView, AuditLogEntity  
**Use Case:** Comprehensive transaction analysis for reconciliation and auditing

## API Reference

### Browse Templates

**List All Templates**
```bash
GET /marketplace/templates
```

Response:
```json
{
  "templates": [
    {
      "templateId": "template-customer-support-v1",
      "name": "Customer Support Agent",
      "description": "24/7 AI assistant for payment inquiries...",
      "category": "customer-support",
      "rating": 4.8,
      "ratingCount": 42,
      "downloads": 156,
      "popularityScore": 8.5,
      "tags": ["support", "payments", "refunds"],
      "author": "Akka Team",
      "version": "1.0.0"
    }
  ]
}
```

**Filter by Category**
```bash
GET /marketplace/templates/category/fraud-detection
```

**Search Templates**
```bash
GET /marketplace/templates/search?query=fraud
```

**Top Rated Templates**
```bash
GET /marketplace/templates/top-rated
```

**Most Popular Templates**
```bash
GET /marketplace/templates/popular
```

### Template Details

**Get Template**
```bash
GET /marketplace/templates/{templateId}
```

Response includes full configuration:
```json
{
  "templateId": "template-fraud-detection-v1",
  "name": "Fraud Detection Analyst",
  "config": {
    "systemPrompt": "You are an expert fraud detection analyst...",
    "tools": [
      {"toolName": "PaymentHistoryView", "config": {}},
      {"toolName": "FraudCheckEntity", "config": {}}
    ],
    "settings": {
      "riskThresholds": "low:0.3,medium:0.6,high:0.8"
    },
    "guardrails": {
      "enabledGuardrails": ["pii-guard", "output-validation"]
    },
    "model": {
      "provider": "openai",
      "modelId": "gpt-4o",
      "temperature": 0.3
    }
  }
}
```

### Template Management

**Publish Template** (Admin)
```bash
POST /marketplace/templates
Content-Type: application/json

{
  "templateId": "my-custom-template-v1",
  "name": "My Custom Agent",
  "description": "Custom agent for specific use case",
  "category": "general",
  "config": { ... },
  "tags": ["custom", "specialized"],
  "author": "Your Team",
  "version": "1.0.0"
}
```

**Update Template** (Admin)
```bash
PUT /marketplace/templates/{templateId}
Content-Type: application/json

{
  "config": { ... }
}
```

**Delete Template** (Admin)
```bash
DELETE /marketplace/templates/{templateId}
```

### Ratings

**Rate Template**
```bash
POST /marketplace/templates/{templateId}/rate
Content-Type: application/json

{
  "rating": 4.5
}
```

Valid ratings: 0.0 - 5.0 (0.5 increments)

### Deployment

**Deploy Template**
```bash
POST /marketplace/deploy/{templateId}
Content-Type: application/json

{
  "agentId": "my-deployed-agent-001",
  "customizations": {
    "customSetting": "value",
    "maxRetries": "3"
  }
}
```

Response:
```json
{
  "deploymentId": "deployment-abc123",
  "templateId": "template-customer-support-v1",
  "agentId": "my-deployed-agent-001",
  "status": "active",
  "deployedAt": "2026-05-23T10:00:00Z"
}
```

Deployment automatically:
- Increments template download count
- Creates active deployment entity
- Applies custom configurations
- Returns deployment ID for tracking

## Web UI

Access the marketplace web interface at:
```
http://localhost:9000/marketplace-ui
```

### Features
- **Visual Template Browser** - Card-based template display with ratings and downloads
- **Search & Filters** - Real-time search and category filtering
- **Template Details Modal** - View full configuration and metadata
- **One-Click Deploy** - Deploy templates with custom configuration UI
- **Rating System** - Rate templates directly from the UI
- **Responsive Design** - Works on desktop, tablet, and mobile

### UI Screenshots

**Marketplace Home**
- Template cards with rating stars
- Category filter dropdown
- Search bar
- Sort options (rating, downloads, name)

**Template Details**
- Full description and use case
- Configuration preview
- Tools and guardrails list
- Rating and download statistics
- Deploy button

**Deploy Modal**
- Agent ID input
- Custom configuration editor (JSON)
- Deploy confirmation

## Domain Model

### AgentTemplate
```java
public record AgentTemplate(
    String templateId,
    String name,
    String description,
    String category,
    AgentConfig config,
    List<String> tags,
    String author,
    String version,
    double rating,           // 0.0 - 5.0
    int ratingCount,
    int downloads,
    double popularityScore,  // Weighted: (rating * 2) + (downloads / 100)
    Instant createdAt,
    Instant updatedAt
)
```

### TemplateDeployment
```java
public record TemplateDeployment(
    String deploymentId,
    String templateId,
    String agentId,
    Map<String, String> customizations,
    String status,           // active, inactive, failed
    Instant deployedAt,
    String errorMessage
)
```

## Views

### AgentTemplateView
Query methods:
- `getAllTemplates()` - All templates sorted by popularity
- `getById(templateId)` - Single template details
- `getByCategory(category)` - Templates in category
- `getTopRated()` - Templates with rating >= 4.0
- `getMostDownloaded()` - Top 50 by downloads
- `searchTemplates(query)` - Full-text search

## Entities

### AgentTemplateEntity (Key-Value)
Commands:
- `createTemplate()` - Publish new template
- `updateTemplate()` - Update configuration
- `rateTemplate(rating)` - Add rating
- `incrementDownloads()` - Track deployment
- `deleteTemplate()` - Remove from marketplace

### TemplateDeploymentEntity (Key-Value)
Commands:
- `deployTemplate()` - Create deployment
- `updateDeployment()` - Update customizations
- `activateDeployment()` - Enable agent
- `deactivateDeployment()` - Disable agent
- `failDeployment(error)` - Mark as failed
- `deleteDeployment()` - Remove deployment

## Best Practices

### Template Selection
1. **Match use case** - Choose template aligned with your needs
2. **Check ratings** - Consider templates with 4+ stars and multiple ratings
3. **Review tools** - Ensure required tools are available in your system
4. **Test before production** - Deploy to test environment first

### Template Customization
1. **Override settings** - Customize via deployment customizations
2. **Adjust temperature** - Lower for deterministic tasks, higher for creative
3. **Configure guardrails** - Enable appropriate safety measures
4. **Set context limits** - Define conversation length and memory

### Template Development
1. **Clear prompts** - Write specific, actionable system prompts
2. **Tool selection** - Only include necessary tools
3. **Guardrail coverage** - Add PII, output validation, toxic language guards
4. **Version control** - Use semantic versioning (1.0.0, 1.1.0, 2.0.0)
5. **Documentation** - Provide clear description and use cases

## Marketplace Initialization

Templates are automatically populated on service startup via `MarketplaceInitializer`:

```java
@Override
public void onStartup() {
    var templates = TemplateLibrary.getAllTemplates();
    for (var template : templates) {
        // Publish each pre-built template
    }
}
```

To disable auto-initialization, remove `MarketplaceInitializer` from `Bootstrap.java`.

## Security Considerations

### Template Publishing
- **Authentication required** - Only authorized users can publish
- **Validation** - All templates validated before publishing
- **Audit logging** - All template changes logged

### Template Deployment
- **Sandbox execution** - Agents run in isolated environments
- **Guardrail enforcement** - Safety checks at runtime
- **Resource limits** - Token and rate limiting per deployment

### PII Protection
- Templates with PII access require `pii-guard` guardrail
- Automatic PII detection in outputs
- Credit card, SSN, email, phone number filtering

## Monitoring

### Template Metrics
- Download count
- Average rating
- Popularity score
- Deployment success rate

### Deployment Metrics
- Active deployments
- Failed deployments
- Error rates
- Resource utilization

## Troubleshooting

### Template Not Found
```
Error: Template template-xxx-v1 does not exist
```
**Solution:** Check template ID spelling, use `/marketplace/templates` to list available templates

### Deployment Failed
```
Status: failed
Error: Agent initialization error
```
**Solution:** 
1. Check agent ID is unique
2. Verify required tools are available
3. Review error logs in deployment entity
4. Validate customizations JSON format

### Low Rating
**Causes:**
- Unclear or generic prompts
- Missing required tools
- Poor guardrail coverage
- Outdated configuration

**Solution:** Update template with better prompts, add missing tools, enable guardrails

## Examples

### Deploy with Custom Configuration
```bash
# Deploy customer support agent with custom settings
curl -X POST http://localhost:9000/marketplace/deploy/template-customer-support-v1 \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "support-agent-prod-001",
    "customizations": {
      "maxConversationTurns": "20",
      "escalationKeywords": "urgent,complaint,manager,lawsuit",
      "responseStyle": "professional",
      "enableAutoRefund": "true",
      "refundThreshold": "100.00"
    }
  }'
```

### Rate and Review
```bash
# Rate template
curl -X POST http://localhost:9000/marketplace/templates/template-fraud-detection-v1/rate \
  -H "Content-Type: application/json" \
  -d '{"rating": 5.0}'

# Get updated rating
curl http://localhost:9000/marketplace/templates/template-fraud-detection-v1
```

### Search and Filter
```bash
# Search for fraud-related templates
curl http://localhost:9000/marketplace/templates/search?query=fraud

# Get all analytics templates
curl http://localhost:9000/marketplace/templates/category/analytics

# Get top-rated templates
curl http://localhost:9000/marketplace/templates/top-rated
```

## Resources

- [AI Agents Guide](AI_AGENTS.md)
- [Agent Chaining](AGENT_CHAINING.md)
- [Akka Agent SDK](https://doc.akka.io/agents)
- [Template Library Source](src/main/java/com/example/payment/application/TemplateLibrary.java)

## Support

For issues with:
- **Template bugs** - Report to template author
- **Marketplace features** - Open GitHub issue
- **Deployment issues** - Check troubleshooting guide
- **Custom templates** - Consult Akka Agent SDK documentation
