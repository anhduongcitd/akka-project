# Research: Online Payment System

**Feature**: 001-online-payment  
**Date**: 2026-05-22  
**Purpose**: Resolve technical unknowns and establish best practices for implementation

## Research Questions

### Q1: Payment Gateway Selection and Integration

**Question**: Which payment gateway should we use for PCI DSS Level 1 compliant processing, and how do we integrate it with Akka SDK?

**Decision**: Use Stripe as the payment gateway

**Rationale**:
- **PCI DSS Level 1 certified** - highest level of compliance
- **Comprehensive SDK** - Java client library available with excellent documentation
- **Digital wallet support** - Native support for Apple Pay, Google Pay, PayPal
- **Multi-currency** - Supports all required currencies (USD, EUR, GBP, JPY, AUD) with automatic exchange rates
- **Tokenization** - Built-in secure tokenization for saved payment methods (no raw card storage)
- **Proven reliability** - 99.99% uptime SLA, used by millions of businesses
- **Testing support** - Complete test mode with test cards and webhooks

**Alternatives considered**:
- **Braintree**: Good alternative but less comprehensive multi-currency support
- **Adyen**: Enterprise-focused, more complex integration, higher cost
- **PayPal Payments**: Limited to PayPal ecosystem, doesn't cover all card types

**Integration approach**:
- Use Stripe Java SDK (`com.stripe:stripe-java:latest`)
- Call Stripe APIs from Workflow steps (not directly from Entity - workflows handle external calls)
- Store Stripe payment intent IDs and customer IDs in domain model
- Use Stripe webhooks to handle async payment confirmations (implement Consumer)
- Never store raw card data - use Stripe tokens only

### Q2: Email Service for Transaction Confirmations

**Question**: How do we send transaction confirmation emails (FR-009) reliably within 5 seconds (SC-005)?

**Decision**: Use AWS SES (Simple Email Service) with async Consumer pattern

**Rationale**:
- **Fast delivery**: Typically <1 second to send, meets 5-second requirement
- **High reliability**: 99.9% SLA with automatic retries
- **Cost-effective**: $0.10 per 1000 emails
- **Easy integration**: AWS SDK for Java available
- **Akka pattern**: Implement as Consumer listening to payment events

**Alternatives considered**:
- **SendGrid**: Good alternative, slightly more expensive
- **Mailgun**: Less reliable delivery times
- **SMTP directly**: No delivery guarantees, harder to scale

**Implementation pattern**:
```java
@Component(id = "email-notification")
@Consume.FromEventSourcedEntity(PaymentTransactionEntity.class)
public class EmailNotificationConsumer extends Consumer {
  private final EmailService emailService;
  
  public Effect onEvent(PaymentTransactionEvent event) {
    if (event instanceof PaymentTransactionEvent.PaymentSucceeded succeeded) {
      emailService.sendConfirmation(succeeded.customerId(), succeeded.transactionId());
    }
    return effects().done();
  }
}
```

### Q3: Real-time Exchange Rates

**Question**: How do we fetch and apply real-time exchange rates for multi-currency support (FR-019)?

**Decision**: Use exchangerate-api.com with caching strategy

**Rationale**:
- **Free tier**: 1500 requests/month free, sufficient for caching strategy
- **Real-time rates**: Updates every 24 hours (sufficient for payment processing)
- **Simple API**: Single REST endpoint, easy integration
- **Reliability**: 99.9% uptime
- **No authentication** required for free tier

**Caching strategy**:
- Cache exchange rates for 1 hour in-memory
- Fetch from API on cache miss
- Fallback to last-known rates if API unavailable
- Store rates with timestamp for audit trail

**Implementation approach**:
```java
public class ExchangeRateService {
  private final Map<String, CachedRate> rateCache = new ConcurrentHashMap<>();
  private final HttpClient httpClient;
  
  public BigDecimal convert(Money amount, Currency targetCurrency) {
    var rate = getRate(amount.currency(), targetCurrency);
    return amount.value().multiply(rate);
  }
  
  private BigDecimal getRate(Currency from, Currency to) {
    // Check cache first (1 hour TTL)
    // Fetch from API if needed
    // Return cached rate on API failure
  }
}
```

**Alternatives considered**:
- **Stripe automatic conversion**: More expensive, less control
- **ECB API**: EU-focused, missing some currencies
- **Paid services (XE, Oanda)**: Unnecessary cost for this use case

### Q4: Duplicate Payment Prevention

**Question**: How do we prevent duplicate payments within 60 seconds (FR-012)?

**Decision**: Use idempotency key pattern in Event Sourced Entity

**Rationale**:
- **Akka SDK native**: Event sourcing provides natural deduplication
- **Simple implementation**: Check recent events before processing
- **No external dependencies**: Pure domain logic

**Implementation**:
```java
public Effect<PaymentResult> processPayment(ProcessPaymentCommand command) {
  // Check last 10 events for duplicate within 60 seconds
  var isDuplicate = eventLog()
    .stream()
    .limit(10)
    .filter(e -> e instanceof PaymentInitiated)
    .map(e -> (PaymentInitiated) e)
    .anyMatch(e -> 
      e.amount().equals(command.amount()) &&
      e.timestamp().isAfter(Instant.now().minusSeconds(60))
    );
    
  if (isDuplicate) {
    return effects().error("Duplicate payment detected");
  }
  
  // Process payment...
}
```

**Alternatives considered**:
- **External cache (Redis)**: Unnecessary complexity, violates Akka SDK First
- **Database table**: Not needed with event sourcing
- **Stripe idempotency keys**: Good backup but we handle it first

### Q5: Payment Session Management

**Question**: How do we handle payment session timeouts (FR-015 - 3 minutes)?

**Decision**: Use Workflow with built-in timeout and compensation

**Rationale**:
- **Akka SDK native**: Workflows have first-class timeout support
- **Automatic cleanup**: Compensation steps handle timeout scenarios
- **Audit trail**: All timeout events recorded

**Implementation**:
```java
@Override
public WorkflowSettings settings() {
  return WorkflowSettings.builder()
    .defaultStepTimeout(ofSeconds(180)) // 3 minutes
    .stepRecovery(
      PaymentProcessingWorkflow::authorizePaymentStep,
      RecoverStrategy.maxRetries(1).failoverTo(PaymentProcessingWorkflow::timeoutStep)
    )
    .build();
}
```

### Q6: PCI DSS Compliance Architecture

**Question**: How do we ensure PCI DSS Level 1 compliance in the architecture?

**Decision**: Never store card data - use Stripe tokenization for all card handling

**Key compliance patterns**:

1. **Card data flow**:
   - Frontend → Stripe.js (client-side) → Stripe servers → Token returned
   - Our service only receives and stores tokens, never raw card data

2. **Storage**:
   - Store: Stripe customer ID, payment method ID, last 4 digits, card brand
   - Never store: Full card number, CVV, expiration date (except MM/YY for display)

3. **Audit trail**:
   - Event sourcing automatically provides complete audit trail
   - All payment events timestamped and immutable

4. **Access control**:
   - Use `@Acl` annotations on endpoints
   - JWT validation for customer authentication
   - Merchant portal requires separate authentication

**Compliance checklist**:
- ✅ No raw card storage (use tokens)
- ✅ All data encrypted in transit (HTTPS)
- ✅ Complete audit trail (event sourcing)
- ✅ Access controls (ACLs, JWT)
- ✅ Session timeouts (3 minutes)
- ✅ Secure external communication (Stripe SDK)

## Dependencies Summary

**External JVM Dependencies** (add to pom.xml):
```xml
<!-- Payment Gateway -->
<dependency>
  <groupId>com.stripe</groupId>
  <artifactId>stripe-java</artifactId>
  <version>24.0.0</version>
</dependency>

<!-- Email Service -->
<dependency>
  <groupId>com.amazonaws</groupId>
  <artifactId>aws-java-sdk-ses</artifactId>
  <version>1.12.600</version>
</dependency>

<!-- HTTP Client for Exchange Rates -->
<!-- (Use built-in java.net.http.HttpClient - no additional dependency) -->
```

**Configuration** (application.conf):
```hocon
payment {
  stripe {
    api-key = ${STRIPE_API_KEY}
    webhook-secret = ${STRIPE_WEBHOOK_SECRET}
  }
  
  email {
    aws-access-key = ${AWS_ACCESS_KEY}
    aws-secret-key = ${AWS_SECRET_KEY}
    from-address = "noreply@example.com"
  }
  
  exchange-rates {
    api-url = "https://api.exchangerate-api.com/v4/latest/"
    cache-ttl-minutes = 60
  }
}
```

## Best Practices

### Workflow Design
- Use workflows for multi-step processes (payment authorization → capture)
- Implement compensation steps for failures
- Set explicit timeouts (3 minutes for payment processing)
- Use limited retries to avoid duplicate charges

### Entity Design
- Event Sourced Entities for payment transactions (need audit trail)
- Event Sourced Entities for saved payment methods (need history)
- Store foreign keys (Stripe IDs) in domain model for traceability

### View Design
- Payment history view consumes PaymentTransactionEntity events
- Index by customer ID for efficient queries
- Include filters for date range, status, amount range

### Security
- Never log card numbers or CVV codes
- Use Stripe test mode for development
- Validate all inputs before calling payment gateway
- Implement rate limiting on payment endpoints

### Testing
- Use Stripe test card numbers for integration tests
- Mock Stripe SDK for unit tests
- Test timeout scenarios with workflow test kit
- Test email delivery with mock email service

## Open Questions / Assumptions

**Assumptions**:
1. STRIPE_API_KEY and AWS credentials will be provided via environment variables
2. Frontend will use Stripe.js for card tokenization (not in scope for backend)
3. Currency exchange rates updated once per hour are acceptable for business requirements
4. Email delivery within 5 seconds includes queueing time (SES handles actual delivery)

**No open questions** - all technical unknowns resolved.
