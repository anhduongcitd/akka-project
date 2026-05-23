# Online Payment Service

[![Tests](https://img.shields.io/badge/tests-58%20passing-brightgreen)](IMPLEMENTATION_SUMMARY.md)
[![Build](https://img.shields.io/badge/build-passing-brightgreen)](IMPLEMENTATION_SUMMARY.md)
[![Status](https://img.shields.io/badge/status-production--ready-blue)](IMPLEMENTATION_SUMMARY.md)

A comprehensive payment processing service built with Akka SDK that handles credit/debit card payments, digital wallets, multi-currency transactions, and refunds with PCI DSS Level 1 compliance.

**Status**: ✅ **Production-Ready MVP** - All 4 core user stories complete with 58/58 tests passing

## Features

- **Payment Processing**: Credit/debit card payments via Stripe gateway
- **Saved Payment Methods**: Securely save and reuse payment methods
- **Payment History**: View transaction history with filtering
- **Refunds**: Full and partial refund processing
- **Multi-Currency**: Support for USD, EUR, GBP, JPY, AUD with real-time exchange rates
- **Email Notifications**: Automated confirmation emails via AWS SES
- **PCI DSS Compliant**: No raw card data stored, tokenization via Stripe

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for local testing)
- Stripe account (test mode)
- AWS SES account (or use mock email service for testing)

## Quick Start

### 1. Environment Setup

Copy the example environment file and configure your API keys:

```bash
cp .env.example .env
# Edit .env with your Stripe test keys, AWS credentials, etc.
```

Required environment variables:
- `STRIPE_API_KEY` - Get from https://dashboard.stripe.com/test/apikeys
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` - AWS credentials for SES
- `SES_FROM_EMAIL` - Verified sender email address in AWS SES
- `EXCHANGE_RATE_API_URL` - Exchange rate API endpoint

### 2. Build the Project

```bash
mvn compile
```

### 3. Run Locally

```bash
# Load environment variables
export $(cat .env | xargs)

# Start the service
mvn compile exec:java
```

Service starts on `http://localhost:9000`

### 4. Test the API

#### Option A: Payment with New Card

Create a test payment using Stripe's test card `4242424242424242`:

```bash
curl -X POST http://localhost:9000/payment/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "amount": {
      "value": "50.00",
      "currency": "USD"
    },
    "cardToken": "tok_visa",
    "merchantReference": "TEST-ORDER-001",
    "customer": {
      "customerId": "cust_123",
      "email": "customer@example.com",
      "name": "John Doe"
    },
    "savePaymentMethod": true
  }'
```

#### Option B: Payment with Saved Payment Method

Use a previously saved payment method for faster checkout:

```bash
curl -X POST http://localhost:9000/payment/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "amount": {
      "value": "50.00",
      "currency": "USD"
    },
    "paymentMethodId": "pm_abc123",
    "merchantReference": "TEST-ORDER-002",
    "customer": {
      "customerId": "cust_123",
      "email": "customer@example.com",
      "name": "John Doe"
    }
  }'
```

Response:
```json
{
  "transactionId": "txn_abc123...",
  "status": "PENDING",
  "amount": {
    "value": "50.00",
    "currency": "USD",
    "formatted": "$50.00"
  },
  "merchantReference": "TEST-ORDER-001",
  "createdAt": "2024-01-15T10:30:00Z",
  "completedAt": null,
  "failureReason": null
}
```

## API Endpoints

### Payments
- `POST /payment/transactions` - Create new payment
- `GET /payment/transactions/{id}` - Get payment status
- `POST /payment/history` - View payment history with filters
- `GET /payment/transactions/{id}/receipt` - Download receipt

### Payment Methods
- `POST /payment/methods` - Save payment method
- `GET /payment/methods?customerId={id}` - List customer's saved methods
- `DELETE /payment/methods/{id}` - Delete payment method
- `PUT /payment/methods/{id}/default` - Set payment method as default

### Refunds
- `POST /payment/transactions/{id}/refunds` - Process refund
- `GET /payment/transactions/{id}/refunds` - List refunds

### Example Usage

#### Save Payment Method

Save a payment method for faster future checkouts:

```bash
curl -X POST http://localhost:9000/payment/methods \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_123",
    "token": "tok_visa",
    "brand": "VISA",
    "last4Digits": "4242",
    "expirationDate": "2025-12",
    "isDefault": true
  }'
```

Response:
```json
{
  "paymentMethodId": "pm_abc123...",
  "customerId": "cust_123",
  "brand": "VISA",
  "last4Digits": "4242",
  "expirationDate": "2025-12",
  "maskedNumber": "**** 4242",
  "isDefault": true,
  "isExpired": false,
  "isExpiringSoon": false,
  "createdAt": "2024-01-15T10:00:00Z"
}
```

#### List Saved Payment Methods

Get all saved payment methods for a customer:

```bash
curl -X GET "http://localhost:9000/payment/methods?customerId=cust_123"
```

Response:
```json
{
  "methods": [
    {
      "paymentMethodId": "pm_abc123...",
      "customerId": "cust_123",
      "brand": "VISA",
      "last4Digits": "4242",
      "expirationDate": "2025-12",
      "maskedNumber": "**** 4242",
      "isDefault": true,
      "isExpired": false,
      "isExpiringSoon": false,
      "createdAt": "2024-01-15T10:00:00Z"
    }
  ]
}
```

#### Delete Payment Method

Remove a saved payment method:

```bash
curl -X DELETE http://localhost:9000/payment/methods/pm_abc123
```

#### Set Default Payment Method

Mark a payment method as the default:

```bash
curl -X PUT http://localhost:9000/payment/methods/pm_abc123/default
```

#### View Payment History

Get all transactions for a customer:

```bash
curl -X POST http://localhost:9000/payment/history \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_123"
  }'
```

Filter by status:

```bash
curl -X POST http://localhost:9000/payment/history \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_123",
    "status": "SUCCEEDED"
  }'
```

Filter by date range:

```bash
curl -X POST http://localhost:9000/payment/history \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_123",
    "startDate": "2024-01-01T00:00:00Z",
    "endDate": "2024-12-31T23:59:59Z"
  }'
```

Response:
```json
{
  "transactions": [
    {
      "transactionId": "txn_abc123",
      "merchantReference": "ORDER-001",
      "amount": {
        "value": "50.00",
        "currency": "USD",
        "formatted": "$50.00"
      },
      "status": "SUCCEEDED",
      "createdAt": "2024-01-15T10:30:00Z",
      "completedAt": "2024-01-15T10:30:05Z",
      "failureReason": null
    }
  ]
}
```

#### Download Receipt

Get text receipt:

```bash
curl -X GET "http://localhost:9000/payment/transactions/txn_abc123/receipt"
```

Get HTML receipt:

```bash
curl -X GET "http://localhost:9000/payment/transactions/txn_abc123/receipt?format=html"
```

#### Initiate Refund

Process a full or partial refund for a successful payment:

```bash
# Partial refund
curl -X POST http://localhost:9000/payment/transactions/txn_abc123/refunds \
  -H "Content-Type: application/json" \
  -d '{
    "amount": {
      "value": "25.00",
      "currency": "USD"
    },
    "reason": "Customer requested partial refund"
  }'
```

Response:
```json
{
  "refundId": "ref_xyz789...",
  "transactionId": "txn_abc123",
  "status": "PENDING",
  "amount": {
    "value": "25.00",
    "currency": "USD",
    "formatted": "$25.00"
  },
  "reason": "Customer requested partial refund"
}
```

#### Get Refunds List

Retrieve all refunds for a transaction:

```bash
curl -X GET http://localhost:9000/payment/transactions/txn_abc123/refunds
```

Response:
```json
{
  "refunds": [
    {
      "refundId": "ref_xyz789...",
      "amount": {
        "value": "25.00",
        "currency": "USD",
        "formatted": "$25.00"
      },
      "status": "SUCCEEDED",
      "reason": "Customer requested partial refund",
      "createdAt": "2024-01-15T11:00:00Z",
      "completedAt": "2024-01-15T11:00:05Z"
    }
  ]
}
```

#### Get Transaction with Refunds

Check payment status including all refunds:

```bash
curl -X GET http://localhost:9000/payment/transactions/txn_abc123
```

### Multi-Currency

#### Get Exchange Rates

Get current exchange rates for all supported currencies:

```bash
curl -X GET http://localhost:9000/payment/exchange-rates
```

Response:
```json
{
  "rates": {
    "USD": "1",
    "EUR": "0.85",
    "GBP": "0.73",
    "JPY": "110.0",
    "AUD": "1.35"
  },
  "baseCurrency": "USD",
  "timestamp": "2026-05-23T12:00:00Z"
}
```

#### Convert Currency

Convert an amount from one currency to another:

```bash
curl -X POST http://localhost:9000/payment/convert \
  -H "Content-Type: application/json" \
  -d '{
    "amount": "100.00",
    "fromCurrency": "USD",
    "toCurrency": "EUR"
  }'
```

Response:
```json
{
  "originalAmount": {
    "value": "100.00",
    "currency": "USD",
    "formatted": "$100.00"
  },
  "convertedAmount": {
    "value": "85.00",
    "currency": "EUR",
    "formatted": "€85.00"
  },
  "exchangeRate": "0.8500",
  "timestamp": "2026-05-23T12:00:00Z"
}
```

## Testing

### Run Unit Tests
```bash
mvn test
```

### Run Integration Tests
```bash
mvn verify
```

### Test Cards (Stripe)
- Success: `4242424242424242`
- Declined: `4000000000000002`
- Insufficient Funds: `4000000000009995`

See full list at [Stripe Test Cards](https://stripe.com/docs/testing#cards)

## Deployment

### Build Container Image
```bash
mvn clean install -DskipTests
```

### Deploy to Akka Platform
```bash
# Install Akka CLI
# See: https://doc.akka.io/reference/cli/index.html

# Login
akka auth login

# Deploy service
akka service deploy online-payment-service online-payment-service:1.0-SNAPSHOT --push

# Set environment variables
akka service env set online-payment-service \
  STRIPE_API_KEY=$STRIPE_API_KEY \
  AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
  AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY
```

## Development

### Project Structure

```
src/main/java/com/example/payment/
├── domain/          # Domain models (no Akka dependencies)
├── application/     # Entities, Views, Workflows
└── api/             # HTTP endpoints
```

### Key Components

- **PaymentTransactionEntity** - Event sourced entity for payment state
- **PaymentProcessingWorkflow** - Orchestrates payment with Stripe
- **PaymentHistoryView** - Query model for transaction history
- **PaymentEndpoint** - REST API endpoints

For detailed development guide, see [quickstart.md](specs/001-online-payment/quickstart.md)

## Documentation

- [Feature Specification](specs/001-online-payment/spec.md)
- [Implementation Plan](specs/001-online-payment/plan.md)
- [Quickstart Guide](specs/001-online-payment/quickstart.md)
- [Data Model](specs/001-online-payment/data-model.md)

## Resources

- [Akka Documentation](https://doc.akka.io)
- [Stripe API Reference](https://stripe.com/docs/api)
- [AWS SES Documentation](https://docs.aws.amazon.com/ses/)

## License

See [LICENSE](LICENSE) file for details.
