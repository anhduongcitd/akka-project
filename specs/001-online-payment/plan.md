# Implementation Plan: Online Payment System

**Branch**: `001-online-payment` | **Date**: 2026-05-22 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-online-payment/spec.md`

## Summary

Build a comprehensive online payment processing service using Akka SDK that handles credit/debit card payments, digital wallets (Apple Pay, Google Pay, PayPal), multi-currency transactions (USD, EUR, GBP, JPY, AUD), and PCI DSS Level 1 compliant payment processing with support for saved payment methods, transaction history, and refunds.

**Technical approach**: Use Event Sourced Entities for payment transactions and saved payment methods to maintain complete audit trail. Implement Workflows for multi-step payment processing with compensation for failures. Use Views for payment history queries. HTTP Endpoints for REST API. External payment gateway integration via ComponentClient.

## Technical Context

**Language/Version**: Java 21 (as per project setup)
**Primary Dependencies**: Akka SDK 3.5.19, payment gateway SDK (Stripe recommended), email service client
**Storage**: Akka Event Sourcing (built-in), no additional database required
**Testing**: JUnit 5, AssertJ, Akka TestKit (EventSourcedTestKit, TestKitSupport)
**Target Platform**: JVM (Linux/Docker containers for deployment)
**Project Type**: Web service with REST API
**Performance Goals**: 10,000 concurrent transactions, <30 second payment completion, 95% success rate
**Constraints**: <3 minute timeout, PCI DSS Level 1 compliance (no raw card storage), real-time exchange rates
**Scale/Scope**: Multi-currency support (5 currencies), 4 digital wallet types, full audit trail

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Akka SDK First (NON-NEGOTIABLE)**: ✅ PASS
- All components use Akka SDK primitives (Event Sourced Entities, Workflows, Views, HTTP Endpoints)
- External dependencies justified:
  - Payment gateway SDK: Required for PCI DSS compliant card processing (cannot implement internally)
  - Email service client: Required for transaction confirmations (FR-009)
  - Exchange rate API client: Required for multi-currency support (FR-019)

**Design Principles**: ✅ PASS
- Domain independence: Payment transaction logic in domain package, no Akka dependencies
- API isolation: Endpoints define own request/response types, not exposing domain internals
- Single responsibility: Each entity handles one concern (PaymentTransaction, PaymentMethod)
- Descriptive naming: PaymentTransactionEntity, PaymentMethodEntity, PaymentHistoryView

**Test Coverage**: ✅ PASS
- Specification includes explicit testing phases for all user stories
- Plan includes unit tests for domain logic, integration tests for entities/views/endpoints

**Simplicity**: ✅ PASS
- Building only required features from spec (P1-P3 priorities)
- No premature abstractions or hypothetical future requirements
- Direct, flat component structure

**Status**: All gates PASSED. Proceeding to Phase 0 research.

## Project Structure

### Documentation (this feature)

```text
specs/001-online-payment/
├── plan.md              # This file (/akka:plan command output)
├── research.md          # Phase 0 output (/akka:plan command)
├── data-model.md        # Phase 1 output (/akka:plan command)
├── quickstart.md        # Phase 1 output (/akka:plan command)
├── contracts/           # Phase 1 output (/akka:plan command)
│   └── http-api.md      # REST API contract
└── tasks.md             # Phase 2 output (/akka:tasks command - NOT created by /akka:plan)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/example/payment/
│   │   ├── domain/
│   │   │   ├── PaymentTransaction.java          # Payment state record
│   │   │   ├── PaymentTransactionEvent.java     # Sealed interface for events
│   │   │   ├── PaymentMethod.java               # Saved payment method state
│   │   │   ├── PaymentMethodEvent.java          # Saved method events
│   │   │   ├── Customer.java                    # Customer reference record
│   │   │   ├── Refund.java                      # Refund record
│   │   │   ├── Money.java                       # Money value object with currency
│   │   │   └── PaymentStatus.java               # Enum: PENDING, SUCCEEDED, FAILED, REFUNDED
│   │   ├── application/
│   │   │   ├── PaymentTransactionEntity.java    # Event sourced entity for payments
│   │   │   ├── PaymentMethodEntity.java         # Event sourced entity for saved methods
│   │   │   ├── PaymentProcessingWorkflow.java   # Orchestrates payment with gateway
│   │   │   ├── RefundWorkflow.java              # Handles refund processing
│   │   │   ├── PaymentHistoryView.java          # Query payment history
│   │   │   ├── CustomerPaymentMethodsView.java  # Query saved payment methods
│   │   │   ├── EmailNotificationConsumer.java   # Send confirmation emails
│   │   │   └── ExchangeRateService.java         # Fetch real-time rates
│   │   └── api/
│   │       ├── PaymentEndpoint.java             # HTTP REST API for payments
│   │       ├── PaymentMethodEndpoint.java       # HTTP REST API for saved methods
│   │       └── RefundEndpoint.java              # HTTP REST API for refunds
│   └── resources/
│       └── application.conf                      # Akka configuration
└── test/
    └── java/com/example/payment/
        ├── domain/
        │   ├── PaymentTransactionTest.java
        │   └── MoneyTest.java
        ├── application/
        │   ├── PaymentTransactionEntityTest.java
        │   ├── PaymentMethodEntityTest.java
        │   ├── PaymentProcessingWorkflowTest.java
        │   ├── PaymentHistoryViewIntegrationTest.java
        │   └── PaymentEndpointIntegrationTest.java
```

**Structure Decision**: Single project structure selected. This is a cohesive payment service with related components. The domain-application-api package structure follows Akka SDK conventions and maintains clear separation of concerns.

## Complexity Tracking

No constitution violations. All external dependencies justified and necessary for PCI DSS compliance and feature requirements.
