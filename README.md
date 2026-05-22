# Online Payment Service

A comprehensive payment processing service built with Akka SDK that handles credit/debit card payments, digital wallets, multi-currency transactions, and refunds with PCI DSS Level 1 compliance.

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
    "savePaymentMethod": true
  }'
```

## API Endpoints

### Payments
- `POST /payment/transactions` - Create new payment
- `GET /payment/transactions/{id}` - Get payment status
- `GET /payment/history` - View payment history

### Payment Methods
- `POST /payment/methods` - Save payment method
- `GET /payment/methods` - List saved methods
- `DELETE /payment/methods/{id}` - Delete payment method

### Refunds
- `POST /payment/transactions/{id}/refunds` - Process refund
- `GET /payment/transactions/{id}/refunds` - List refunds

### Multi-Currency
- `GET /payment/exchange-rates` - Get current rates
- `POST /payment/convert` - Convert currencies

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
