# Quickstart Guide: Online Payment System

**Feature**: 001-online-payment  
**Date**: 2026-05-22  
**Audience**: Developers setting up and testing the payment system

## Prerequisites

- Java 21+
- Maven 3.9+
- Akka CLI installed
- Docker (for local testing)
- Stripe account (test mode)
- AWS SES account (or use mock email service for testing)

## Environment Setup

### 1. Configure Stripe Test Keys

Create `.env` file in project root:

```bash
# Stripe Test Keys (get from https://dashboard.stripe.com/test/apikeys)
STRIPE_API_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# AWS SES (or use mock for local testing)
AWS_ACCESS_KEY=your_access_key
AWS_SECRET_KEY=your_secret_key
AWS_REGION=us-east-1

# Exchange Rate API (free tier, no key needed)
EXCHANGE_RATE_API_URL=https://api.exchangerate-api.com/v4/latest/
```

### 2. Install Dependencies

```bash
# Add to pom.xml (see research.md for versions)
mvn clean install
```

### 3. Build the Service

```bash
export JAVA_HOME="/path/to/java21"
mvn compile
```

## Running Locally

### Start the Service

```bash
# Load environment variables
export $(cat .env | xargs)

# Run with Akka local runtime
mvn compile exec:java
```

Service will start on `http://localhost:9000`

### Verify Service is Running

```bash
curl http://localhost:9000/health
# Expected: {"status":"UP"}
```

## Testing the Payment Flow

### Step 1: Create a Customer Session

```bash
# In production, customer would log in and receive JWT
# For testing, we'll use a test customer ID
export CUSTOMER_ID="test_cust_12345"
export JWT_TOKEN="<your-test-jwt>"  # Generate via auth service
```

### Step 2: Make a Test Payment

Use Stripe test card: `4242424242424242`

```bash
curl -X POST http://localhost:9000/payment/transactions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "amount": {
      "value": "50.00",
      "currency": "USD"
    },
    "cardToken": "tok_visa",
    "merchantReference": "TEST-ORDER-001",
    "savePaymentMethod": true
  }'
```

**Expected Response**:
```json
{
  "transactionId": "txn_...",
  "status": "PENDING",
  "amount": {
    "value": "50.00",
    "currency": "USD"
  },
  "createdAt": "2026-05-22T..."
}
```

### Step 3: Check Payment Status

```bash
# Get transaction ID from previous response
export TXN_ID="txn_..."

curl http://localhost:9000/payment/transactions/$TXN_ID \
  -H "Authorization: Bearer $JWT_TOKEN"
```

**Expected**: Status should transition from `PENDING` → `SUCCEEDED` within a few seconds

### Step 4: View Payment History

```bash
curl "http://localhost:9000/payment/history?limit=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Step 5: List Saved Payment Methods

```bash
curl http://localhost:9000/payment/methods \
  -H "Authorization: Bearer $JWT_TOKEN"
```

**Expected**: Should show the card you just saved (if `savePaymentMethod: true`)

### Step 6: Make Payment with Saved Method

```bash
# Get payment method ID from previous response
export PM_ID="pm_..."

curl -X POST http://localhost:9000/payment/transactions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "amount": {
      "value": "25.00",
      "currency": "USD"
    },
    "paymentMethodId": "'$PM_ID'",
    "merchantReference": "TEST-ORDER-002"
  }'
```

### Step 7: Process a Refund (Merchant)

```bash
# Use merchant JWT token
export MERCHANT_JWT="<merchant-jwt>"

curl -X POST http://localhost:9000/payment/transactions/$TXN_ID/refunds \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $MERCHANT_JWT" \
  -d '{
    "amount": {
      "value": "10.00",
      "currency": "USD"
    },
    "reason": "Test partial refund"
  }'
```

## Testing Stripe Test Cards

### Successful Payments

```
4242424242424242  - Visa (generic success)
5555555555554444  - Mastercard
378282246310005   - American Express
6011111111111117  - Discover
```

### Declined Cards

```
4000000000000002  - Card declined (generic)
4000000000009995  - Insufficient funds
4000000000009987  - Lost card
4000000000009979  - Stolen card
```

### Special Behaviors

```
4000000000000341  - Requires authentication (3D Secure)
4000002500003155  - Successful payment, but charge later expires
```

See [Stripe Test Cards](https://stripe.com/docs/testing#cards) for complete list.

## Multi-Currency Testing

### Get Current Exchange Rates

```bash
curl http://localhost:9000/payment/exchange-rates
```

### Convert Currency

```bash
curl -X POST http://localhost:9000/payment/convert \
  -H "Content-Type: application/json" \
  -d '{
    "amount": {
      "value": "100.00",
      "currency": "USD"
    },
    "targetCurrency": "EUR"
  }'
```

### Make Payment in Different Currency

```bash
curl -X POST http://localhost:9000/payment/transactions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "amount": {
      "value": "50.00",
      "currency": "EUR"
    },
    "cardToken": "tok_visa",
    "merchantReference": "TEST-EUR-001"
  }'
```

## Testing Edge Cases

### Duplicate Payment Prevention

```bash
# Make same payment twice quickly
for i in {1..2}; do
  curl -X POST http://localhost:9000/payment/transactions \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d '{
      "amount": {"value": "99.99", "currency": "USD"},
      "cardToken": "tok_visa",
      "merchantReference": "DUPLICATE-TEST"
    }'
  sleep 1
done

# Second request should return 409 Conflict
```

### Payment Timeout

```bash
# Use special test card that requires authentication (will timeout after 3 min)
curl -X POST http://localhost:9000/payment/transactions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "amount": {"value": "50.00", "currency": "USD"},
    "cardToken": "tok_threeDSecure",
    "merchantReference": "TIMEOUT-TEST"
  }'

# Don't complete 3D Secure - transaction should timeout and fail
```

### Card Expiration Warning

```bash
# Add card that expires soon (within 30 days)
# View payment methods - should see isExpiring: true
```

## Monitoring and Debugging

### Check Service Logs

```bash
# Follow logs in terminal where service is running
# Look for payment processing workflow steps
```

### View Stripe Dashboard

1. Go to https://dashboard.stripe.com/test/payments
2. View all test payments
3. Check webhook deliveries

### View Akka Backoffice (Local)

```bash
# Access local backoffice UI
open http://localhost:9000/akka/backoffice

# View entity states, event logs, workflow progress
```

## Running Tests

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
# Start service first
mvn verify
```

### Specific Test Suites

```bash
# Domain logic tests
mvn test -Dtest=PaymentTransactionTest

# Entity tests
mvn test -Dtest=PaymentTransactionEntityTest

# Workflow tests
mvn test -Dtest=PaymentProcessingWorkflowTest

# Endpoint tests
mvn verify -Dit.test=PaymentEndpointIntegrationTest
```

## Deploying to Akka Platform

### Build Container Image

```bash
mvn clean install -DskipTests
```

### Push and Deploy

```bash
# Login to Akka
akka auth login

# Create project (first time only)
akka project create payment-system --region us-east1

# Deploy service
akka service deploy payment-service payment-service:latest --push

# Set environment variables
akka service env set payment-service \
  STRIPE_API_KEY=$STRIPE_API_KEY \
  AWS_ACCESS_KEY=$AWS_ACCESS_KEY \
  AWS_SECRET_KEY=$AWS_SECRET_KEY
```

### Create Route (Expose Public URL)

```bash
akka route create \
  --service payment-service \
  --hostname payment.example.com \
  --path /payment
```

## Troubleshooting

### Payment Fails with "Configuration Error"

**Check**: Stripe API keys are set correctly
```bash
echo $STRIPE_API_KEY  # Should start with sk_test_
```

### Email Confirmations Not Sending

**Check**: AWS SES credentials and region
```bash
# Verify SES email is verified in AWS console
# In SES sandbox mode, both sender and recipient must be verified
```

### Exchange Rates Not Loading

**Check**: Exchange rate API is accessible
```bash
curl https://api.exchangerate-api.com/v4/latest/USD
```

### Duplicate Detection Not Working

**Check**: System time is synchronized (important for 60-second window)

### Refund Fails

**Check**: 
- Transaction is in SUCCEEDED status (cannot refund pending/failed)
- Refund amount doesn't exceed original amount minus already refunded
- Using merchant JWT token (not customer token)

## Next Steps

1. **Implement Frontend**: Use Stripe.js to tokenize cards client-side
2. **Add Monitoring**: Integrate with your observability stack
3. **Security Hardening**: Review ACLs, rate limits, JWT validation
4. **Production Keys**: Replace test keys with production Stripe/AWS keys
5. **Webhook Handlers**: Implement webhook consumers for async events
6. **Receipt Generation**: Implement PDF receipt generation (FR for download)

## Support

- **Akka Documentation**: https://doc.akka.io
- **Stripe API Reference**: https://stripe.com/docs/api
- **Project Issues**: <your-repo>/issues
