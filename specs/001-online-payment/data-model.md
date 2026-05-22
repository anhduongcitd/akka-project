# Data Model: Online Payment System

**Feature**: 001-online-payment  
**Date**: 2026-05-22  
**Source**: Extracted from feature specification and research

## Entities

### PaymentTransaction (Event Sourced Entity)

**Purpose**: Represents a single payment attempt with complete audit trail

**State Record**:
```java
public record PaymentTransaction(
  String transactionId,           // Unique transaction ID (entity ID)
  String customerId,               // Customer reference
  Money amount,                    // Payment amount with currency
  PaymentStatus status,            // PENDING, SUCCEEDED, FAILED, REFUNDED
  String stripePaymentIntentId,   // Stripe payment intent ID
  PaymentMethod paymentMethod,     // How payment was made
  Instant createdAt,               // When transaction was created
  Instant completedAt,             // When transaction completed (null if pending)
  String merchantReference,        // Merchant's order/invoice ID
  List<Refund> refunds            // List of refunds applied
)
```

**Events** (sealed interface `PaymentTransactionEvent`):
- `PaymentInitiated` - Payment started
  - Fields: transactionId, customerId, amount, paymentMethod, merchantReference, timestamp
- `PaymentAuthorized` - Payment authorized by gateway
  - Fields: transactionId, stripePaymentIntentId, timestamp
- `PaymentSucceeded` - Payment captured successfully
  - Fields: transactionId, timestamp
- `PaymentFailed` - Payment failed
  - Fields: transactionId, reason, timestamp
- `RefundInitiated` - Refund started
  - Fields: transactionId, refundId, amount, reason, timestamp
- `RefundCompleted` - Refund processed
  - Fields: transactionId, refundId, timestamp

**Validation Rules**:
- Amount must be between $0.01 and $999,999.99 (FR-007)
- Cannot refund more than original payment amount
- Cannot refund a failed payment
- Duplicate prevention: Check last 10 events for same amount within 60 seconds

**State Transitions**:
```
PENDING → SUCCEEDED (payment captured)
PENDING → FAILED (payment declined/timeout)
SUCCEEDED → REFUNDED (full refund)
SUCCEEDED → SUCCEEDED (partial refund, status unchanged)
```

---

### PaymentMethod (Event Sourced Entity)

**Purpose**: Represents a saved payment method for faster checkout

**State Record**:
```java
public record PaymentMethod(
  String paymentMethodId,          // Unique ID (entity ID)
  String customerId,               // Owner customer ID
  String stripePaymentMethodId,    // Stripe payment method token
  String last4Digits,              // Last 4 digits for display
  CardBrand cardBrand,             // VISA, MASTERCARD, AMEX, etc.
  int expirationMonth,             // 1-12
  int expirationYear,              // e.g., 2026
  Instant createdAt,               // When saved
  boolean isDefault                // Default payment method for customer
)
```

**Events** (sealed interface `PaymentMethodEvent`):
- `PaymentMethodAdded` - New payment method saved
  - Fields: paymentMethodId, customerId, stripePaymentMethodId, last4Digits, cardBrand, expiration, timestamp
- `PaymentMethodSetAsDefault` - Marked as default
  - Fields: paymentMethodId, customerId, timestamp
- `PaymentMethodRemoved` - Payment method deleted
  - Fields: paymentMethodId, customerId, timestamp

**Validation Rules**:
- Expiration date must be in the future
- Only one default payment method per customer
- Cannot delete payment method with pending transactions

---

## Value Objects

### Money

**Purpose**: Represents a monetary amount with currency

```java
public record Money(
  BigDecimal value,
  Currency currency
) {
  public Money {
    Objects.requireNonNull(value, "Value cannot be null");
    Objects.requireNonNull(currency, "Currency cannot be null");
    if (value.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Value cannot be negative");
    }
  }
  
  public Money add(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("Cannot add different currencies");
    }
    return new Money(value.add(other.value), currency);
  }
  
  public Money subtract(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("Cannot subtract different currencies");
    }
    return new Money(value.subtract(other.value), currency);
  }
  
  public Money convert(Currency targetCurrency, BigDecimal exchangeRate) {
    if (currency.equals(targetCurrency)) {
      return this;
    }
    return new Money(value.multiply(exchangeRate), targetCurrency);
  }
}
```

---

### Refund

**Purpose**: Represents a refund transaction linked to original payment

```java
public record Refund(
  String refundId,                 // Unique refund ID
  Money amount,                    // Refund amount
  RefundStatus status,             // PENDING, COMPLETED, FAILED
  String reason,                   // Why refund was issued
  Instant initiatedAt,             // When refund started
  Instant completedAt              // When refund completed (null if pending)
)
```

---

### Customer

**Purpose**: Customer reference for payment system

```java
public record Customer(
  String customerId,               // Unique customer ID
  String email,                    // For sending confirmations
  String name                      // Display name
)
```

---

## Enums

### PaymentStatus

```java
public enum PaymentStatus {
  PENDING,      // Payment initiated, not yet completed
  SUCCEEDED,    // Payment captured successfully
  FAILED,       // Payment declined or failed
  REFUNDED      // Payment fully refunded
}
```

### RefundStatus

```java
public enum RefundStatus {
  PENDING,      // Refund initiated, processing
  COMPLETED,    // Refund processed successfully
  FAILED        // Refund failed
}
```

### CardBrand

```java
public enum CardBrand {
  VISA,
  MASTERCARD,
  AMERICAN_EXPRESS,
  DISCOVER,
  DINERS_CLUB,
  JCB,
  UNIONPAY
}
```

### Currency

```java
public enum Currency {
  USD("US Dollar"),
  EUR("Euro"),
  GBP("British Pound"),
  JPY("Japanese Yen"),
  AUD("Australian Dollar");
  
  private final String displayName;
  
  Currency(String displayName) {
    this.displayName = displayName;
  }
  
  public String getDisplayName() {
    return displayName;
  }
}
```

---

## Views

### PaymentHistoryView

**Purpose**: Query payment transactions by customer, date range, status

**Row Record**:
```java
public record PaymentHistoryEntry(
  String transactionId,
  String customerId,
  Money amount,
  PaymentStatus status,
  String merchantReference,
  Instant createdAt,
  Instant completedAt
)
```

**Query Methods**:
- `getByCustomer(customerId)` - All transactions for a customer
- `getByCustomerAndDateRange(customerId, startDate, endDate)` - Filtered by date
- `getByCustomerAndStatus(customerId, status)` - Filtered by status
- `getByTransactionId(transactionId)` - Single transaction details

**Consumes**: `PaymentTransactionEntity` events

---

### CustomerPaymentMethodsView

**Purpose**: Query saved payment methods by customer

**Row Record**:
```java
public record PaymentMethodEntry(
  String paymentMethodId,
  String customerId,
  String last4Digits,
  CardBrand cardBrand,
  int expirationMonth,
  int expirationYear,
  boolean isDefault,
  boolean isExpiring  // True if expires within 30 days
)
```

**Query Methods**:
- `getByCustomer(customerId)` - All payment methods for customer
- `getDefaultPaymentMethod(customerId)` - Customer's default payment method

**Consumes**: `PaymentMethodEntity` events

---

## Relationships

```
Customer (1) ──── (*) PaymentTransaction
Customer (1) ──── (*) PaymentMethod
PaymentTransaction (1) ──── (*) Refund

PaymentMethod ← (used by) → PaymentTransaction
```

**Key Foreign Keys**:
- PaymentTransaction.customerId → Customer.customerId
- PaymentTransaction.stripePaymentIntentId → Stripe Payment Intent
- PaymentMethod.customerId → Customer.customerId
- PaymentMethod.stripePaymentMethodId → Stripe Payment Method
- Refund.refundId → Stripe Refund ID

---

## Data Flow

1. **Create Payment**:
   ```
   Customer → PaymentEndpoint 
           → PaymentProcessingWorkflow 
           → PaymentTransactionEntity (PaymentInitiated event)
           → Stripe API (authorize)
           → PaymentTransactionEntity (PaymentAuthorized event)
           → Stripe API (capture)
           → PaymentTransactionEntity (PaymentSucceeded event)
           → EmailNotificationConsumer (send confirmation)
   ```

2. **Save Payment Method**:
   ```
   Customer → PaymentMethodEndpoint
           → Stripe API (create payment method)
           → PaymentMethodEntity (PaymentMethodAdded event)
   ```

3. **Query Payment History**:
   ```
   Customer → PaymentEndpoint
           → PaymentHistoryView (query)
           → Return PaymentHistoryEntry records
   ```

4. **Process Refund**:
   ```
   Merchant → RefundEndpoint
           → RefundWorkflow
           → PaymentTransactionEntity (RefundInitiated event)
           → Stripe API (refund)
           → PaymentTransactionEntity (RefundCompleted event)
           → EmailNotificationConsumer (send refund confirmation)
   ```

---

## Storage Considerations

**Event Journal Size Estimates**:
- Average transaction: 3-5 events (initiate, authorize, succeed/fail)
- 10,000 transactions/day = 30,000-50,000 events/day
- Event sourcing handles this natively with snapshots

**View Table Sizes**:
- PaymentHistoryView: 1 row per transaction
- CustomerPaymentMethodsView: ~2-3 rows per active customer on average

**No additional database required** - Akka SDK event journal and view projections handle all persistence.
