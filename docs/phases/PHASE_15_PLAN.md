# Phase 15 Plan: Multi-modal Agent Capabilities

## Overview

Phase 15 extends the AI agent system with multi-modal capabilities, enabling agents to process and understand images, documents, audio, and combine multiple modalities in sophisticated workflows.

## Goals

1. **Image Processing** - Receipt OCR, fraud detection from images, visual analysis
2. **Document Processing** - PDF/Word extraction, invoice processing, document Q&A
3. **Audio Processing** - Transcription, voice-based queries, sentiment analysis
4. **Multi-modal Chains** - Combine text + image + audio in workflows
5. **Media Storage** - Secure storage and retrieval for uploaded files

## Architecture

### Components
```
┌─────────────────────────────────────────────────────────────┐
│               Multi-modal Agent System                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │    Image     │  │   Document   │  │    Audio     │     │
│  │  Processing  │  │  Processing  │  │  Processing  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │    Media     │  │  Multi-modal │  │   Content    │     │
│  │   Storage    │  │    Chains    │  │  Extraction  │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   Receipt    │  │   Invoice    │  │  Compliance  │     │
│  │     OCR      │  │  Processing  │  │   Scanner    │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Use Cases

### Payment Industry Specific
1. **Receipt Processing** - Upload receipt image → extract amount, merchant, date
2. **Fraud Detection** - Analyze ID card/driver's license images for authenticity
3. **Invoice Processing** - Extract line items, totals, payment terms from PDF invoices
4. **Compliance Documents** - Process KYC documents (passport, utility bills)
5. **Chargeback Evidence** - Analyze images/documents submitted as proof

### General Use Cases
6. **Document Q&A** - Ask questions about uploaded PDF/Word documents
7. **Image Description** - Generate descriptions for product images
8. **Audio Transcription** - Convert customer support calls to text
9. **Multi-modal Analysis** - Analyze receipt image + transaction data together

## Implementation Phases

### Phase 1: Media Storage Infrastructure (2 hours)

#### 1.1 Media Storage Domain
**Domain:**
- `MediaFile.java` - File metadata (ID, type, size, URL, checksum)
- `MediaType.java` - Enum (IMAGE, DOCUMENT, AUDIO, VIDEO)
- `MediaMetadata.java` - Extracted metadata (dimensions, duration, pages)

**Features:**
- MIME type validation (image/*, application/pdf, audio/*)
- File size limits (10MB for images, 50MB for documents)
- Checksum calculation (SHA-256)
- Virus scanning integration point

#### 1.2 Media Storage Entity
**Application:**
- `MediaStorageEntity.java` - Media file registry (KVE)

**Commands:**
- `uploadMedia()` - Register uploaded file
- `getMedia()` - Retrieve file metadata
- `deleteMedia()` - Mark file for deletion
- `updateMetadata()` - Add extracted metadata

**Storage Strategy:**
- **Development:** Local filesystem (`/tmp/media/`)
- **Production:** S3-compatible storage (AWS S3, MinIO)
- Store metadata in entity, file content in blob storage
- Generate pre-signed URLs for secure access

#### 1.3 Media Views
**Application:**
- `MediaStorageView.java` - Query media files

**Queries:**
- `getByCustomer(customerId)` - Customer's uploaded files
- `getByType(mediaType)` - Filter by media type
- `getRecent(limit)` - Recent uploads
- `getByTransaction(transactionId)` - Files linked to transaction

### Phase 2: Image Processing Agents (3 hours)

#### 2.1 Receipt OCR Agent
**Agent:**
- `ReceiptOCRAgent.java` - Extract data from receipt images

**Capabilities:**
- Detect text regions using vision model
- Extract structured data:
  - Merchant name
  - Transaction date and time
  - Total amount
  - Line items (description, quantity, price)
  - Tax breakdown
  - Payment method (last 4 digits if visible)
- Confidence scoring per field
- Handle rotated/skewed images

**Model:**
- GPT-4 Vision / Claude 3 Opus with vision
- Temperature: 0.1 (high accuracy needed)

**Response Format:**
```java
public record ReceiptData(
    String merchantName,
    String merchantAddress,
    Instant transactionDate,
    Money totalAmount,
    List<LineItem> items,
    Money tax,
    Money subtotal,
    String paymentMethodLast4,
    double confidenceScore
)
```

#### 2.2 Image Analysis Agent
**Agent:**
- `ImageAnalysisAgent.java` - General image understanding

**Capabilities:**
- Describe image content
- Detect objects and text
- Identify fraud indicators (tampering, duplicates)
- Extract visible text (OCR)
- Classify image type (receipt, invoice, ID, bank statement)

**Use Cases:**
- Fraud detection (fake IDs, photoshopped documents)
- Image classification for routing
- Content moderation (inappropriate images)

#### 2.3 Document Verification Agent
**Agent:**
- `DocumentVerificationAgent.java` - Verify ID documents

**Capabilities:**
- Detect document type (passport, driver's license, utility bill)
- Extract personal information (name, DOB, address)
- Verify document authenticity indicators
- Check expiration dates
- Compare photo with live image (fraud detection)

**Compliance:**
- PII guardrail (sensitive data handling)
- Audit logging (all document access logged)
- Encryption at rest and in transit

### Phase 3: Document Processing Agents (2.5 hours)

#### 3.1 PDF/Document Parser
**Application:**
- `DocumentParserService.java` - Extract text from PDFs

**Features:**
- Text extraction from PDF (Apache PDFBox)
- Text extraction from Word docs (Apache POI)
- Table extraction
- Image extraction from documents
- Preserve document structure (headings, paragraphs)

#### 3.2 Invoice Processing Agent
**Agent:**
- `InvoiceProcessingAgent.java` - Extract invoice data

**Capabilities:**
- Identify invoice type (tax invoice, proforma, credit note)
- Extract header info (invoice #, date, due date, PO #)
- Extract parties (vendor, customer, addresses)
- Extract line items with pricing
- Calculate totals and verify math
- Detect payment terms (Net 30, Due on Receipt)

**Response Format:**
```java
public record InvoiceData(
    String invoiceNumber,
    String poNumber,
    Instant invoiceDate,
    Instant dueDate,
    Party vendor,
    Party customer,
    List<InvoiceLineItem> lineItems,
    Money subtotal,
    Money tax,
    Money total,
    String paymentTerms,
    String currency
)
```

#### 3.3 Document Q&A Agent
**Agent:**
- `DocumentQAAgent.java` - Answer questions about documents

**Capabilities:**
- Load document content (from MediaStorageEntity)
- Parse and chunk document (max 4000 tokens per chunk)
- Answer questions based on document content
- Cite page numbers or sections
- Handle multi-page documents

**Example:**
```
User: "What is the payment due date?"
Agent: "According to page 1 of the invoice, the payment is due on June 30, 2026."
```

### Phase 4: Audio Processing Agents (2 hours)

#### 4.1 Audio Transcription Agent
**Agent:**
- `AudioTranscriptionAgent.java` - Convert speech to text

**Capabilities:**
- Transcribe audio files (mp3, wav, m4a)
- Speaker diarization (identify different speakers)
- Timestamp generation
- Support multiple languages
- Filter profanity (optional)

**Integration:**
- Whisper API (OpenAI)
- Or local Whisper model
- Max duration: 30 minutes
- Chunk long audio files

**Response Format:**
```java
public record Transcription(
    String text,
    List<Segment> segments,
    String language,
    double duration,
    List<Speaker> speakers
)
```

#### 4.2 Audio Sentiment Agent
**Agent:**
- `AudioSentimentAgent.java` - Analyze sentiment from transcription

**Capabilities:**
- Analyze transcript sentiment (positive, neutral, negative)
- Detect emotions (anger, frustration, satisfaction)
- Identify escalation indicators
- Extract key topics discussed

**Use Cases:**
- Customer support call analysis
- Dispute resolution sentiment
- Agent performance evaluation

### Phase 5: Multi-modal Chains (2.5 hours)

#### 5.1 Multi-modal Chain Templates
**Templates:**

1. **Receipt Verification Chain** (Sequential)
   - Step 1: Receipt OCR → extract data
   - Step 2: Transaction Analyzer → compare with transaction
   - Step 3: Fraud Analyst → flag discrepancies
   
2. **Document KYC Chain** (Sequential)
   - Step 1: Document Verification → verify ID
   - Step 2: Image Analysis → check authenticity
   - Step 3: Compliance Agent → approve/reject

3. **Invoice Processing Chain** (Parallel)
   - Step 1a: Invoice Processing → extract data
   - Step 1b: Document Q&A → answer specific questions
   - Step 2: Summarizer → combine results

4. **Call Analysis Chain** (Sequential)
   - Step 1: Audio Transcription → speech to text
   - Step 2: Audio Sentiment → analyze tone
   - Step 3: Customer Support → generate response

5. **Chargeback Evidence Chain** (Parallel)
   - Step 1a: Receipt OCR → extract receipt data
   - Step 1b: Document Q&A → extract evidence from docs
   - Step 1c: Transaction Analyzer → get transaction details
   - Step 2: Summarizer → compile evidence report

#### 5.2 Multi-modal Input Support
**Update AgentChainConfig:**
```java
public record ChainStep(
    String stepId,
    String agentId,
    String inputTemplate,
    Map<String, String> parameters,
    String outputKey,
    Condition condition,
    List<MediaAttachment> attachments  // NEW
)

public record MediaAttachment(
    String mediaId,
    MediaType type,
    String purpose  // "receipt", "invoice", "id-document"
)
```

**Agent Invocation:**
- Support multi-modal inputs (text + image URLs)
- Pass media URLs to vision-capable models
- Handle mixed inputs (some steps text-only, some multi-modal)

### Phase 6: API & UI (2.5 hours)

#### 6.1 Media Upload API
**Endpoint:**
- `MediaUploadEndpoint.java` - Handle file uploads

**Endpoints:**
```
POST   /media/upload                       # Upload file
GET    /media/{mediaId}                    # Get metadata
GET    /media/{mediaId}/download           # Download file
DELETE /media/{mediaId}                    # Delete file
GET    /media/customer/{customerId}        # List customer files
POST   /media/{mediaId}/process            # Trigger processing
```

**Upload Process:**
1. Accept multipart/form-data
2. Validate file type and size
3. Calculate checksum
4. Upload to blob storage
5. Create MediaStorageEntity
6. Return media ID and URL

#### 6.2 Multi-modal Agent API
**Endpoint:**
- `MultiModalAgentEndpoint.java` - Multi-modal agent operations

**Endpoints:**
```
POST   /agents/receipt-ocr                 # OCR receipt image
POST   /agents/image-analysis              # Analyze image
POST   /agents/document-verify             # Verify ID document
POST   /agents/invoice-process             # Process invoice
POST   /agents/document-qa                 # Ask about document
POST   /agents/audio-transcribe            # Transcribe audio
POST   /agents/audio-sentiment             # Analyze audio sentiment
```

**Request Format:**
```json
{
  "mediaId": "media_abc123",
  "query": "What is the merchant name?",
  "options": {
    "language": "en",
    "confidenceThreshold": 0.8
  }
}
```

#### 6.3 Multi-modal UI
**Web:**
- `multimodal.html` - Multi-modal agent interface

**Features:**
- File upload widget (drag & drop)
- Image preview
- PDF viewer
- Audio player
- OCR results display
- Interactive Q&A interface
- Multi-file batch upload

### Phase 7: Pre-built Multi-modal Templates (1.5 hours)

#### 7.1 Receipt Processing Template
**Template:**
- Pre-configured receipt OCR agent
- Input: Receipt image
- Output: Structured receipt data

**Deployment:**
```bash
curl -X POST http://localhost:9000/marketplace/deploy/template-receipt-ocr-v1 \
  -d '{"agentId": "receipt-processor"}'
```

#### 7.2 Invoice Processing Template
**Template:**
- Invoice processing agent with document parser
- Input: Invoice PDF
- Output: Structured invoice data

#### 7.3 Document KYC Template
**Template:**
- Document verification for KYC
- Input: ID document image
- Output: Extracted data + verification result

#### 7.4 Call Transcription Template
**Template:**
- Audio transcription + sentiment analysis
- Input: Audio file
- Output: Transcript + sentiment

### Phase 8: Testing (2.5 hours)

**Unit Tests:**
- `MediaStorageEntityTest.java` (8 tests)
- `ReceiptOCRAgentTest.java` (10 tests)
- `ImageAnalysisAgentTest.java` (8 tests)
- `DocumentVerificationAgentTest.java` (10 tests)
- `InvoiceProcessingAgentTest.java` (10 tests)
- `DocumentQAAgentTest.java` (8 tests)
- `AudioTranscriptionAgentTest.java` (8 tests)
- `MultiModalChainTest.java` (10 tests)

**Integration Tests:**
- `MediaUploadIntegrationTest.java` (8 tests)
- `ReceiptOCRIntegrationTest.java` (6 tests)
- `InvoiceProcessingIntegrationTest.java` (6 tests)
- `AudioTranscriptionIntegrationTest.java` (6 tests)
- `MultiModalChainIntegrationTest.java` (8 tests)
- `MultiModalEndpointIntegrationTest.java` (10 tests)

**Test Data:**
- Sample receipt images (various formats, quality levels)
- Sample invoices (PDF, different layouts)
- Sample ID documents (blurred for testing)
- Sample audio files (different accents, quality)

**Total:** 116 tests

### Phase 9: Documentation (1.5 hours)

**Documentation:**
- `MULTIMODAL_AGENTS.md` - Complete multi-modal guide
  - Overview and capabilities
  - Image processing guide
  - Document processing guide
  - Audio processing guide
  - Multi-modal chains
  - API reference
  - Best practices
  - Security considerations

- Update `README.md` with Phase 15 features
- Update `AGENT_MARKETPLACE.md` with multi-modal templates

## Implementation Order

1. **Phase 1** - Media Storage (foundation)
2. **Phase 2** - Image Processing (receipt OCR, image analysis, document verification)
3. **Phase 3** - Document Processing (PDF parser, invoice, Q&A)
4. **Phase 4** - Audio Processing (transcription, sentiment)
5. **Phase 5** - Multi-modal Chains (combine modalities)
6. **Phase 6** - API & UI (upload, processing endpoints, UI)
7. **Phase 7** - Pre-built Templates (4 multi-modal templates)
8. **Phase 8** - Testing (unit + integration)
9. **Phase 9** - Documentation

## Key Features

### Image Processing
- ✅ Receipt OCR with structured data extraction
- ✅ ID document verification
- ✅ Image fraud detection
- ✅ Vision-capable model integration (GPT-4V, Claude 3)
- ✅ Confidence scoring

### Document Processing
- ✅ PDF text extraction (Apache PDFBox)
- ✅ Invoice data extraction
- ✅ Document Q&A
- ✅ Table extraction
- ✅ Multi-page document support

### Audio Processing
- ✅ Speech-to-text transcription (Whisper)
- ✅ Speaker diarization
- ✅ Sentiment analysis
- ✅ Multi-language support
- ✅ Timestamp generation

### Multi-modal Chains
- ✅ Text + Image workflows
- ✅ Text + Document workflows
- ✅ Text + Audio workflows
- ✅ Mixed media inputs per step
- ✅ 5 pre-built multi-modal chains

### Security
- ✅ Secure file storage (S3/MinIO)
- ✅ Pre-signed URLs (time-limited access)
- ✅ File type validation
- ✅ Virus scanning integration
- ✅ PII guardrails on extracted data

## Domain Models

### MediaFile
```java
public record MediaFile(
    String mediaId,
    String customerId,
    MediaType mediaType,           // IMAGE, DOCUMENT, AUDIO
    String mimeType,               // image/jpeg, application/pdf, audio/mpeg
    String filename,
    long sizeBytes,
    String storageUrl,             // S3 URL or file path
    String checksum,               // SHA-256
    MediaMetadata metadata,        // Dimensions, duration, pages
    Instant uploadedAt,
    Instant expiresAt,             // Auto-delete after 30 days
    String transactionId,          // Optional link to transaction
    ProcessingStatus status        // PENDING, PROCESSING, COMPLETED, FAILED
)
```

### ReceiptData
```java
public record ReceiptData(
    String merchantName,
    String merchantAddress,
    Instant transactionDate,
    Money totalAmount,
    Money subtotal,
    Money tax,
    List<LineItem> items,
    String paymentMethodLast4,
    double confidenceScore,
    Map<String, Double> fieldConfidence  // Per-field confidence
)

public record LineItem(
    String description,
    int quantity,
    Money unitPrice,
    Money totalPrice
)
```

### InvoiceData
```java
public record InvoiceData(
    String invoiceNumber,
    String poNumber,
    Instant invoiceDate,
    Instant dueDate,
    Party vendor,
    Party customer,
    List<InvoiceLineItem> lineItems,
    Money subtotal,
    Money tax,
    Money shipping,
    Money total,
    String paymentTerms,
    String currency,
    List<String> notes
)

public record Party(
    String name,
    String address,
    String taxId,
    String phone,
    String email
)
```

### Transcription
```java
public record Transcription(
    String text,
    List<TranscriptSegment> segments,
    String language,
    double durationSeconds,
    List<Speaker> speakers
)

public record TranscriptSegment(
    String text,
    double startSeconds,
    double endSeconds,
    int speakerId
)
```

## API Endpoints Summary

**Total:** 14 new endpoints

```
Media Management:
POST   /media/upload                       # Upload file (multipart)
GET    /media/{mediaId}                    # Get file metadata
GET    /media/{mediaId}/download           # Download file (pre-signed URL)
DELETE /media/{mediaId}                    # Delete file
GET    /media/customer/{customerId}        # List customer's files
POST   /media/{mediaId}/process            # Trigger processing

Multi-modal Agents:
POST   /agents/receipt-ocr                 # Extract receipt data
POST   /agents/image-analysis              # Analyze image
POST   /agents/document-verify             # Verify ID document
POST   /agents/invoice-process             # Process invoice PDF
POST   /agents/document-qa                 # Q&A about document
POST   /agents/audio-transcribe            # Transcribe audio
POST   /agents/audio-sentiment             # Analyze audio sentiment

UI:
GET    /multimodal-ui                      # Multi-modal interface
```

## Views

1. **MediaStorageView** - Query uploaded media files
2. **MediaProcessingView** - Track processing status
3. **ReceiptDataView** - Extracted receipt data
4. **InvoiceDataView** - Extracted invoice data
5. **TranscriptionView** - Transcription results

## Technical Considerations

### Model Selection
- **Vision Models:**
  - GPT-4 Vision (best accuracy, higher cost)
  - Claude 3 Opus with vision (good balance)
  - Claude 3 Haiku with vision (fast, cheaper)
  
- **Audio Models:**
  - Whisper API (OpenAI, managed)
  - Whisper local (open source, self-hosted)
  
- **Document Models:**
  - GPT-4 Turbo (long context, 128k tokens)
  - Claude 3 Opus (100k tokens)

### Storage Strategy
**Development:**
- Local filesystem: `/tmp/media/{customerId}/{mediaId}`
- Simple, no external dependencies

**Production:**
- AWS S3 or MinIO (S3-compatible)
- Bucket structure: `media/{customerId}/{year}/{month}/{mediaId}`
- Server-side encryption (SSE-S3 or SSE-KMS)
- Lifecycle policy (auto-delete after 30 days)

### Performance
- **Image processing:** 2-5 seconds per image
- **PDF processing:** 1-3 seconds per page
- **Audio transcription:** ~0.1x realtime (10 min audio = 1 min processing)
- **Caching:** Cache extracted data for 1 hour

### Cost Optimization
- **Image compression:** Reduce size before sending to model
- **Thumbnail generation:** Use thumbnails for preview
- **Batch processing:** Process multiple receipts together
- **Result caching:** Cache OCR results by checksum

### Security
- **File validation:** Magic byte checking (not just extension)
- **Virus scanning:** ClamAV or VirusTotal integration
- **Access control:** Pre-signed URLs expire after 1 hour
- **PII redaction:** Automatic redaction of sensitive data
- **Audit logging:** Log all file access

### Error Handling
- **Unsupported format:** Return clear error message
- **Corrupted file:** Detect and reject early
- **Model timeout:** Retry with fallback model
- **OCR failure:** Return partial results + confidence scores
- **Storage failure:** Queue for retry

## Expected Test Count

- Unit tests: 72 tests
- Integration tests: 44 tests
- **Total: 116 tests**

## Use Case Examples

### Example 1: Receipt Verification
```bash
# 1. Upload receipt image
curl -X POST http://localhost:9000/media/upload \
  -F "file=@receipt.jpg" \
  -F "customerId=cust_123"
# Returns: {"mediaId": "media_abc123"}

# 2. Extract receipt data
curl -X POST http://localhost:9000/agents/receipt-ocr \
  -H "Content-Type: application/json" \
  -d '{
    "mediaId": "media_abc123"
  }'
# Returns: ReceiptData with extracted fields

# 3. Compare with transaction
curl -X POST http://localhost:9000/agent-chains/chain-receipt-verification-v1/execute \
  -d '{
    "initialContext": {
      "receiptMediaId": "media_abc123",
      "transactionId": "txn_456"
    }
  }'
# Returns: Verification result (match/mismatch)
```

### Example 2: Invoice Processing
```bash
# 1. Upload invoice PDF
curl -X POST http://localhost:9000/media/upload \
  -F "file=@invoice.pdf" \
  -F "customerId=vendor_789"
# Returns: {"mediaId": "media_def456"}

# 2. Process invoice
curl -X POST http://localhost:9000/agents/invoice-process \
  -d '{"mediaId": "media_def456"}'
# Returns: InvoiceData with line items, totals

# 3. Ask questions
curl -X POST http://localhost:9000/agents/document-qa \
  -d '{
    "mediaId": "media_def456",
    "query": "What is the payment due date?"
  }'
# Returns: "Payment is due on June 30, 2026 (page 1)"
```

### Example 3: Call Analysis
```bash
# 1. Upload call recording
curl -X POST http://localhost:9000/media/upload \
  -F "file=@call.mp3" \
  -F "customerId=cust_123"
# Returns: {"mediaId": "media_ghi789"}

# 2. Execute call analysis chain
curl -X POST http://localhost:9000/agent-chains/chain-call-analysis-v1/execute \
  -d '{
    "initialContext": {
      "audioMediaId": "media_ghi789"
    }
  }'
# Returns: {
#   "transcription": "...",
#   "sentiment": "frustrated",
#   "recommended_response": "..."
# }
```

## Success Criteria

### Image Processing
- ✅ Receipt OCR accuracy > 95% for standard receipts
- ✅ Processing time < 5 seconds per image
- ✅ Support images up to 10MB
- ✅ Handle rotated/skewed images (auto-rotation)

### Document Processing
- ✅ Invoice extraction accuracy > 90%
- ✅ Support PDFs up to 50 pages
- ✅ Processing time < 3 seconds per page
- ✅ Table extraction with 85% accuracy

### Audio Processing
- ✅ Transcription accuracy > 90% (clear audio)
- ✅ Processing speed > 10x realtime
- ✅ Support audio up to 30 minutes
- ✅ Speaker diarization with 80% accuracy

### Security
- ✅ All files encrypted at rest
- ✅ Pre-signed URLs expire after 1 hour
- ✅ Virus scanning on upload (if enabled)
- ✅ PII guardrails active on extracted data

## Risk Mitigation

**Risk:** Large file uploads overwhelm server  
**Mitigation:** 10MB limit for images, 50MB for documents, streaming upload

**Risk:** Vision model hallucinations  
**Mitigation:** Confidence scoring, multiple verification steps, human review for high-value

**Risk:** Storage costs  
**Mitigation:** Auto-delete after 30 days, compression, lifecycle policies

**Risk:** PII leakage from documents  
**Mitigation:** PII guardrails, audit logging, encryption, access controls

**Risk:** Model API costs  
**Mitigation:** Caching, batching, image compression, cheaper models for simple tasks

## Estimated Effort

- **Phase 1** (Media Storage): 2 hours
- **Phase 2** (Image Processing): 3 hours
- **Phase 3** (Document Processing): 2.5 hours
- **Phase 4** (Audio Processing): 2 hours
- **Phase 5** (Multi-modal Chains): 2.5 hours
- **Phase 6** (API & UI): 2.5 hours
- **Phase 7** (Pre-built Templates): 1.5 hours
- **Phase 8** (Testing): 2.5 hours
- **Phase 9** (Documentation): 1.5 hours

**Total: ~20 hours**

**MVP** (Media + Receipt OCR + Invoice): ~7.5 hours

## Next Steps

After Phase 15, consider:
- **Phase 16**: Agent Governance (compliance, data residency, GDPR)
- **Phase 17**: Advanced Analytics (predictive insights, anomaly detection)
- **Phase 18**: Enterprise Integration (SSO, RBAC, audit exports)
- **Phase 19**: Real-time Streaming (WebSocket agents, live transcription)

---

**Plan Status:** ✅ Ready for Implementation
