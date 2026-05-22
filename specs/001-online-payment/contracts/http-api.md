# HTTP API Contract: Online Payment System

**Feature**: 001-online-payment  
**Date**: 2026-05-22  
**API Style**: REST  
**Base URL**: `/payment`

## Authentication

All endpoints require JWT authentication with customer or merchant claims:
- **Customer endpoints**: Require `customerId` claim in JWT
- **Merchant endpoints**: Require `merchantId` claim in JWT

## Payment Processing API

### POST /payment/transactions

**Purpose**: Initiate a new payment transaction

**Request**:
```json
{
  "amount": {
    "value": "99.99",
    "currency": "USD"
  },
  "paymentMethodId": "pm_12345",  // Optional - saved payment method ID
  "cardToken": "tok_visa",         // Optional - Stripe card token (if not using saved method)
  "merchantReference": "ORDER-123",
  "savePaymentMethod": false       // Save card for future use
}
```

**Response** (201 Created):
```json
{
  "transactionId": "txn_abc123",
  "status": "PENDING",
  "amount": {
    "value": "99.99",
    "currency": "USD"
  },
  "createdAt": "2026-05-22T10:30:00Z"
}
```

**Error Responses**:
- `400 Bad Request` - Invalid amount, missing payment method
- `402 Payment Required` - Insufficient funds, card declined
- `409 Conflict` - Duplicate payment detected
- `429 Too Many Requests` - Rate limit exceeded

---

### GET /payment/transactions/{transactionId}

**Purpose**: Get payment transaction details

**Response** (200 OK):
```json
{
  "transactionId": "txn_abc123",
  "customerId": "cust_xyz",
  "amount": {
    "value": "99.99",
    "currency": "USD"
  },
  "status": "SUCCEEDED",
  "paymentMethod": {
    "type": "CARD",
    "last4": "4242",
    "brand": "VISA"
  },
  "merchantReference": "ORDER-123",
  "createdAt": "2026-05-22T10:30:00Z",
  "completedAt": "2026-05-22T10:30:15Z",
  "refunds": []
}
```

**Error Responses**:
- `404 Not Found` - Transaction not found
- `403 Forbidden` - Not authorized to view this transaction

---

### GET /payment/history

**Purpose**: Query payment transaction history

**Query Parameters**:
- `startDate` (optional) - ISO 8601 date (e.g., `2026-05-01`)
- `endDate` (optional) - ISO 8601 date
- `status` (optional) - `PENDING`, `SUCCEEDED`, `FAILED`, `REFUNDED`
- `limit` (optional, default 50) - Max results
- `offset` (optional, default 0) - Pagination offset

**Response** (200 OK):
```json
{
  "transactions": [
    {
      "transactionId": "txn_abc123",
      "amount": {
        "value": "99.99",
        "currency": "USD"
      },
      "status": "SUCCEEDED",
      "merchantReference": "ORDER-123",
      "createdAt": "2026-05-22T10:30:00Z",
      "completedAt": "2026-05-22T10:30:15Z"
    }
  ],
  "total": 127,
  "limit": 50,
  "offset": 0
}
```

---

### GET /payment/transactions/{transactionId}/receipt

**Purpose**: Download payment receipt as PDF

**Response** (200 OK):
- Content-Type: `application/pdf`
- Content-Disposition: `attachment; filename="receipt-txn_abc123.pdf"`

**Error Responses**:
- `404 Not Found` - Transaction not found
- `403 Forbidden` - Not authorized to view receipt

---

## Saved Payment Methods API

### POST /payment/methods

**Purpose**: Save a payment method for future use

**Request**:
```json
{
  "cardToken": "tok_visa",        // Stripe card token from client-side
  "setAsDefault": true
}
```

**Response** (201 Created):
```json
{
  "paymentMethodId": "pm_12345",
  "last4": "4242",
  "brand": "VISA",
  "expirationMonth": 12,
  "expirationYear": 2026,
  "isDefault": true
}
```

**Error Responses**:
- `400 Bad Request` - Invalid card token
- `402 Payment Required` - Card validation failed

---

### GET /payment/methods

**Purpose**: List customer's saved payment methods

**Response** (200 OK):
```json
{
  "paymentMethods": [
    {
      "paymentMethodId": "pm_12345",
      "last4": "4242",
      "brand": "VISA",
      "expirationMonth": 12,
      "expirationYear": 2026,
      "isDefault": true,
      "isExpiring": false
    },
    {
      "paymentMethodId": "pm_67890",
      "last4": "1234",
      "brand": "MASTERCARD",
      "expirationMonth": 3,
      "expirationYear": 2026,
      "isDefault": false,
      "isExpiring": true    // Expires within 30 days
    }
  ]
}
```

---

### DELETE /payment/methods/{paymentMethodId}

**Purpose**: Remove a saved payment method

**Response** (204 No Content)

**Error Responses**:
- `404 Not Found` - Payment method not found
- `403 Forbidden` - Not authorized to delete
- `409 Conflict` - Cannot delete, has pending transactions

---

### PUT /payment/methods/{paymentMethodId}/default

**Purpose**: Set a payment method as default

**Response** (200 OK):
```json
{
  "paymentMethodId": "pm_12345",
  "isDefault": true
}
```

---

## Refund API

### POST /payment/transactions/{transactionId}/refunds

**Purpose**: Initiate a refund (merchant endpoint)

**Request**:
```json
{
  "amount": {
    "value": "50.00",     // Optional - defaults to full amount
    "currency": "USD"
  },
  "reason": "Customer returned product"
}
```

**Response** (201 Created):
```json
{
  "refundId": "ref_xyz789",
  "transactionId": "txn_abc123",
  "amount": {
    "value": "50.00",
    "currency": "USD"
  },
  "status": "PENDING",
  "initiatedAt": "2026-05-22T15:00:00Z"
}
```

**Error Responses**:
- `400 Bad Request` - Invalid refund amount
- `404 Not Found` - Transaction not found
- `409 Conflict` - Transaction not refundable (failed, already fully refunded)
- `403 Forbidden` - Not authorized (merchant only)

---

### GET /payment/transactions/{transactionId}/refunds

**Purpose**: List refunds for a transaction

**Response** (200 OK):
```json
{
  "refunds": [
    {
      "refundId": "ref_xyz789",
      "amount": {
        "value": "50.00",
        "currency": "USD"
      },
      "status": "COMPLETED",
      "reason": "Customer returned product",
      "initiatedAt": "2026-05-22T15:00:00Z",
      "completedAt": "2026-05-22T15:00:30Z"
    }
  ]
}
```

---

## Currency Conversion API

### GET /payment/exchange-rates

**Purpose**: Get current exchange rates for supported currencies

**Response** (200 OK):
```json
{
  "baseCurrency": "USD",
  "rates": {
    "EUR": 0.85,
    "GBP": 0.73,
    "JPY": 110.25,
    "AUD": 1.35
  },
  "timestamp": "2026-05-22T10:00:00Z"
}
```

---

### POST /payment/convert

**Purpose**: Convert amount between currencies

**Request**:
```json
{
  "amount": {
    "value": "100.00",
    "currency": "USD"
  },
  "targetCurrency": "EUR"
}
```

**Response** (200 OK):
```json
{
  "originalAmount": {
    "value": "100.00",
    "currency": "USD"
  },
  "convertedAmount": {
    "value": "85.00",
    "currency": "EUR"
  },
  "exchangeRate": 0.85,
  "timestamp": "2026-05-22T10:00:00Z"
}
```

---

## Error Response Format

All error responses follow this format:

```json
{
  "error": {
    "code": "PAYMENT_DECLINED",
    "message": "The card was declined",
    "details": {
      "reason": "insufficient_funds",
      "declineCode": "insufficient_funds"
    }
  }
}
```

**Common Error Codes**:
- `INVALID_REQUEST` - Malformed request body
- `PAYMENT_DECLINED` - Card declined by gateway
- `DUPLICATE_PAYMENT` - Duplicate detected within 60 seconds
- `INSUFFICIENT_FUNDS` - Card has insufficient funds
- `EXPIRED_CARD` - Card has expired
- `REFUND_FAILED` - Refund could not be processed
- `RATE_LIMIT_EXCEEDED` - Too many requests
- `UNAUTHORIZED` - Invalid or missing JWT
- `FORBIDDEN` - Valid JWT but insufficient permissions
- `NOT_FOUND` - Resource does not exist
- `CONFLICT` - Operation conflicts with current state

---

## Rate Limiting

- **Payment endpoints**: 10 requests per minute per customer
- **Query endpoints**: 60 requests per minute per customer
- **Refund endpoints**: 5 requests per minute per merchant

Rate limit headers included in responses:
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7
X-RateLimit-Reset: 1653220800
```

---

## Webhooks (Future Enhancement)

**Note**: Not in scope for initial implementation, but API design allows for:
- `payment.succeeded` - Payment completed
- `payment.failed` - Payment failed
- `refund.completed` - Refund processed

---

## OpenAPI Specification

Full OpenAPI 3.0 specification will be generated from endpoint annotations and available at:
- `/openapi.json` - Machine-readable spec
- `/swagger-ui` - Interactive documentation
