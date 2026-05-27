# 📚 Training Plan: Akka Agentic AI cho Fresher Scala

> **Đối tượng:** Fresher đã biết Scala cơ bản (val/var, case class, Option, Future, collections)  
> **Mục tiêu cuối series:** Tự tay xây dựng một hệ thống Multi-Agent AI chạy được trên Akka Platform  
> **Ngôn ngữ chính trong code examples:** Java (Akka SDK Java API) — dễ đọc hơn cho fresher  
> **Tổng số bài:** 12 bài | Thời lượng ước tính: 8–10 tuần nếu học 1–2 bài/tuần

---

## 🗺️ SERIES ROADMAP

```
PHASE 1 — NỀN TẢNG          PHASE 2 — XÂY DỰNG AGENT      PHASE 3 — HỆ THỐNG THỰC TẾ
─────────────────────        ──────────────────────────     ───────────────────────────
Bài 1 → Bài 3               Bài 4 → Bài 8                  Bài 9 → Bài 12
Tư duy & cài đặt            Core components                 Production-ready
```

---

## PHASE 1 — NỀN TẢNG (Bài 1–3)

### 📌 Bài 1: Agentic AI là gì? Tại sao chọn Akka?
**Mục tiêu:** Hiểu bức tranh toàn cảnh trước khi code  
**Nội dung:**
- Chatbot vs AI Agent vs Agentic AI System — điểm khác nhau cốt lõi
- Vòng lặp Perceive → Reason → Act → Observe của một Agent
- Các bài toán thực tế: customer support agent, coding agent, data pipeline agent
- Vì sao Agentic AI khó: state, concurrency, fault tolerance, scale
- Akka giải quyết những vấn đề đó như thế nào (overview, chưa code)
- So sánh nhanh Akka vs LangChain vs CrewAI vs Temporal

**Output sau bài:** Vẽ được sơ đồ kiến trúc một Agentic System đơn giản

---

### 📌 Bài 2: Cài đặt môi trường & "Hello Agent" đầu tiên
**Mục tiêu:** Chạy được agent đầu tiên trên máy local trong 30 phút  
**Nội dung:**
- Cài đặt: JDK 21, Maven/Gradle, Akka CLI, Docker (optional)
- Tạo project mới bằng `akka code init`
- Cấu trúc thư mục của một Akka project
- Viết `GreetingAgent` — agent đơn giản nhận câu hỏi, trả lời qua LLM
- Chạy local với `akka local console`
- Gọi thử agent qua HTTP endpoint
- Đọc log và hiểu agent đang làm gì

**Code ví dụ:**
```java
@ComponentId("greeting-agent")
public class GreetingAgent extends Agent {
    public Effect<String> ask(String question) {
        return effects()
            .systemMessage("You are a helpful assistant.")
            .userMessage(question)
            .thenReply();
    }
}
```

**Output sau bài:** Project Akka chạy được local, gọi API thấy response từ LLM

---

### 📌 Bài 3: Actor Model — Trái tim của Akka
**Mục tiêu:** Hiểu Actor Model để không bị "mù" khi đọc error log và design hệ thống  
**Nội dung:**
- Vấn đề của concurrent programming truyền thống (shared state, deadlock, race condition)
- Actor là gì? Mailbox, message, behavior
- Akka Cluster: actors phân tán trên nhiều node
- Sharding: mỗi Entity/Agent instance sống ở đâu trong cluster?
- Passivation & Recovery: Agent bị tắt rồi khởi động lại — state có mất không?
- Liên hệ với Agentic AI: mỗi Agent session = một Actor instance
- Minh họa bằng sơ đồ (không cần code Actor thủ công, Akka SDK tự quản lý)

**Output sau bài:** Giải thích được tại sao Akka Agent tự scale và tự heal

---

## PHASE 2 — XÂY DỰNG AGENT (Bài 4–8)

### 📌 Bài 4: Entity & State — Agent ghi nhớ thông tin
**Mục tiêu:** Xây dựng agent có persistent state (nhớ thông tin qua các session)  
**Nội dung:**
- Sự khác nhau giữa stateless Agent và stateful Entity
- Event Sourcing cơ bản: state được tái tạo từ chuỗi events
- Khi nào dùng Key-Value Entity vs Event Sourced Entity?
- Xây dựng `UserProfileEntity`: lưu tên, preference của user
- Agent đọc Entity để cá nhân hóa câu trả lời
- Thực hành: `BookmarkAgent` — agent lưu và tìm lại bookmark của user

**Code ví dụ:**
```java
@ComponentId("user-profile")
public class UserProfileEntity extends KeyValueEntity<UserProfile> {
    public Effect<String> updateName(String name) {
        var updated = currentState().withName(name);
        return effects().updateState(updated).thenReply("Updated!");
    }
}
```

**Output sau bài:** Agent nhớ thông tin user giữa các cuộc trò chuyện khác nhau

---

### 📌 Bài 5: Workflow — Agent thực hiện multi-step task
**Mục tiêu:** Xây dựng agent hoàn thành tác vụ nhiều bước, có thể retry nếu lỗi  
**Nội dung:**
- Tại sao cần Workflow? (LLM call có thể fail, bước sau phụ thuộc bước trước)
- Akka Workflow component: định nghĩa steps, transitions, compensations
- Durable execution: workflow tự resume sau khi pod crash
- Timeout và retry policy cho từng step
- Thực hành: `ReportGenerationWorkflow` — agent thu thập dữ liệu → phân tích → tạo báo cáo
- Human-in-the-loop: workflow dừng chờ human approve trước khi tiếp tục

**Output sau bài:** Workflow 3 bước chạy được, tự retry khi lỗi

---

### 📌 Bài 6: Session Memory — Agent nhớ ngữ cảnh hội thoại
**Mục tiêu:** Hiểu cách Akka quản lý conversation history và context window  
**Nội dung:**
- Short-term memory vs Long-term memory trong Agentic AI
- Session memory trong Akka: mỗi sessionId → một memory store
- Context window management: khi history quá dài thì làm gì?
  - Truncation strategy
  - Summarization strategy
- Shared memory: nhiều agent cùng đọc một memory store
- Thực hành: `TutorAgent` — agent dạy học nhớ student đã học đến đâu
- Xem memory dump trong Akka Console

**Output sau bài:** Agent duy trì cuộc trò chuyện coherent qua nhiều turn

---

### 📌 Bài 7: Tool Use — Agent gọi API và thực thi hành động
**Mục tiêu:** Cho Agent khả năng tương tác với thế giới bên ngoài  
**Nội dung:**
- Tool Use (Function Calling) là gì? LLM quyết định khi nào gọi tool
- Cách định nghĩa tool trong Akka Agent bằng annotation `@Tool`
- Retry và timeout khi tool call thất bại
- Các loại tool phổ biến:
  - REST API call (gọi weather API, search API)
  - Database query
  - File read/write
  - Gọi Akka Entity/Workflow khác
- Thực hành: `ResearchAgent` — agent tìm kiếm web, đọc URL, tóm tắt nội dung
- Security: validate tool input, giới hạn quyền của agent

**Code ví dụ:**
```java
@Tool(description = "Search the web for current information")
public String webSearch(String query) {
    return searchService.search(query);
}
```

**Output sau bài:** Agent tự động gọi tool đúng lúc, handle lỗi gracefully

---

### 📌 Bài 8: Streaming — Real-time response với Server-Sent Events
**Mục tiêu:** Trả về response theo luồng thay vì chờ LLM generate xong toàn bộ  
**Nội dung:**
- Tại sao streaming quan trọng cho UX (ChatGPT-style typing effect)
- Akka Streaming component và back-pressure
- Expose Agent response dưới dạng SSE (Server-Sent Events)
- Streaming với tool use: stream text, dừng khi cần gọi tool, tiếp tục stream
- Thực hành: `StreamingChatAgent` với frontend demo đơn giản
- Xử lý client disconnect gracefully

**Output sau bài:** API endpoint stream token by token, frontend hiển thị real-time

---

## PHASE 3 — HỆ THỐNG THỰC TẾ (Bài 9–12)

### 📌 Bài 9: Multi-Agent System — Nhiều Agent cộng tác
**Mục tiêu:** Thiết kế và xây dựng hệ thống nhiều agent phân công nhau  
**Nội dung:**
- Các pattern Multi-Agent: Orchestrator–Worker, Peer-to-Peer, Hierarchical
- Giao tiếp giữa agents trong Akka: ComponentClient (sync) và Event-driven (async)
- Chia nhỏ task phức tạp thành subtask cho từng agent chuyên biệt
- Tránh vòng lặp vô tận giữa các agent
- Thực hành: `ContentPipelineSystem`
  - `PlannerAgent` nhận yêu cầu, chia task
  - `ResearchAgent` thu thập tài liệu
  - `WriterAgent` viết nội dung
  - `ReviewerAgent` kiểm tra và đề xuất sửa
- Visualize agent graph trong Akka Console

**Output sau bài:** Hệ thống 3–4 agent phối hợp hoàn thành một task phức tạp

---

### 📌 Bài 10: MCP Integration — Kết nối hệ sinh thái AI
**Mục tiêu:** Dùng MCP để agent tương tác với hàng trăm tool có sẵn  
**Nội dung:**
- MCP (Model Context Protocol) là gì và tại sao quan trọng
- Akka Agent consume MCP server: kết nối, list tools, gọi tool
- Expose Akka Agent như một MCP server để tool khác dùng
- Các MCP server phổ biến: filesystem, database, GitHub, Slack, Google Drive
- Thực hành: `DevAssistantAgent` dùng MCP để đọc code repo, tạo PR, comment GitHub issue
- Bảo mật MCP connection: auth, rate limit

**Output sau bài:** Agent tích hợp được với ít nhất 2 MCP server bên ngoài

---

### 📌 Bài 11: Evaluation & Observability — Đo chất lượng Agent
**Mục tiêu:** Biết agent đang hoạt động tốt hay xấu, tại sao, và sửa như thế nào  
**Nội dung:**
- Tại sao testing Agent khó hơn testing code thường
- Các metric cần theo dõi: accuracy, latency, token cost, tool call rate
- Akka inline evaluation: chạy evaluator song song với agent
- Tracing với OpenTelemetry: trace một request qua nhiều agent
- Akka Console: visualize component graph, request traces
- Prompt versioning: thay đổi prompt, so sánh kết quả A/B
- Thực hành: Viết evaluator cho `TutorAgent` ở bài 6, dashboard theo dõi chất lượng

**Output sau bài:** Dashboard monitoring cho agent system đang chạy

---

### 📌 Bài 12: Deploy Production — Scale, Multi-region, Fault Tolerance
**Mục tiêu:** Đưa hệ thống từ local lên production với SLA 99.99%  
**Nội dung:**
- Akka Automated Operations: deploy lên cloud (AWS/GCP/Azure) hoặc self-managed
- Elastic scaling: agent tự scale khi load tăng
- Multi-region replication: agent chạy ở nhiều region, tự failover
- Disaster recovery: simulate crash một region, xem hệ thống recover
- Cost optimization: passivation, token budget, caching LLM response
- CI/CD pipeline cho Akka service
- Checklist trước khi go-live: security, auth, rate limiting, logging
- Recap toàn bộ series và hướng đi tiếp theo

**Output sau bài:** Service chạy trên production, có thể demo failover scenario

---

## 📊 TỔNG KẾT PLAN

| Phase | Bài | Tuần đề xuất | Kỹ năng tích lũy |
|-------|-----|--------------|-------------------|
| Nền tảng | 1–3 | Tuần 1–2 | Tư duy Agentic AI, môi trường, Actor Model |
| Xây dựng Agent | 4–8 | Tuần 3–6 | State, Workflow, Memory, Tool Use, Streaming |
| Hệ thống thực tế | 9–12 | Tuần 7–10 | Multi-Agent, MCP, Observability, Production |

---

## 🔧 PREREQUISITES

Trước khi bắt đầu series này, bạn cần biết:
- ✅ Scala cơ bản: `val/var`, `case class`, `Option`, `List`, `Map`, pattern matching
- ✅ Khái niệm HTTP (request/response, REST API, JSON)
- ✅ Biết dùng terminal / command line
- ✅ Hiểu cơ bản về AI/LLM (không cần sâu)

Không cần biết trước:
- ❌ Distributed systems
- ❌ Reactive programming / Future composition nâng cao
- ❌ Kubernetes / Docker
- ❌ Java (series sẽ giải thích phần Java khi cần)

---

## 📦 PROJECT THỰC HÀNH XUYÊN SUỐT

Mỗi bài sẽ xây dựng một feature mới cho **`StudyBuddy`** — một AI learning assistant:

```
StudyBuddy
├── GreetingAgent          (Bài 2)  — chào hỏi, giới thiệu
├── UserProfileEntity      (Bài 4)  — nhớ thông tin học viên
├── StudyPlanWorkflow      (Bài 5)  — tạo lộ trình học
├── TutorAgent             (Bài 6)  — dạy và nhớ ngữ cảnh
├── ResourceFinderAgent    (Bài 7)  — tìm tài liệu học
├── StreamingChatAgent     (Bài 8)  — chat real-time
├── QuizMasterAgent        (Bài 9)  — ra đề, chấm điểm (Multi-Agent)
├── GitHubAgent (MCP)      (Bài 10) — review code bài tập
└── Dashboard              (Bài 11) — theo dõi tiến độ học
```

Đến Bài 12, bạn deploy toàn bộ `StudyBuddy` lên production. 🎓

---

*Series được viết bởi: Senior Agentic AI Engineer | Cập nhật lần cuối: 2026*
