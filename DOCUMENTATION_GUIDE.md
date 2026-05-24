# Documentation Guide

## Documentation Structure

Tất cả documentation đã được tổ chức vào thư mục `docs/` để giữ root directory gọn gàng:

```
akka-project/
├── README.md                    # Main project overview (giữ lại ở root)
├── AGENTS.md                    # Akka agent guidelines (giữ lại ở root)
├── CLAUDE.md                    # AI assistant config (giữ lại ở root)
├── SECURITY.md                  # Security docs (giữ lại ở root)
├── pom.xml                      # Maven config
├── src/                         # Source code
├── specs/                       # Specification documents
└── docs/                        # 📁 All other documentation
    ├── README.md                # Documentation index
    ├── phases/                  # Phase planning & summaries
    │   ├── PHASE_12_SUMMARY.md
    │   ├── PHASE_13_PLAN.md
    │   ├── PHASE_13_SUMMARY.md
    │   ├── PHASE_14_PLAN.md
    │   ├── PHASE_14_SUMMARY.md
    │   └── PHASE_15_PLAN.md
    ├── features/                # Feature-specific docs
    │   ├── AI_AGENTS.md
    │   ├── AGENT_MARKETPLACE.md
    │   └── AGENT_CHAINING.md
    └── implementation/          # Implementation docs
        ├── IMPLEMENTATION_STATUS.md
        ├── IMPLEMENTATION_SUMMARY.md
        ├── IMPLEMENTATION_COMPLETE.md
        └── PROJECT_STRUCTURE_REPORT.md
```

## Files in Root (4 files only)

Chỉ giữ lại các file documentation quan trọng nhất ở root:

1. **README.md** - Project overview (file chính)
2. **AGENTS.md** - Akka agent development guidelines
3. **CLAUDE.md** - AI assistant configuration
4. **SECURITY.md** - Security documentation

## Documentation Categories

### 📁 docs/phases/
Phase-by-phase development documentation:
- Planning documents
- Implementation summaries
- Test results
- Lessons learned

### 📁 docs/features/
Feature-specific documentation:
- AI Agents system
- Agent Marketplace
- Agent Chaining
- Future features

### 📁 docs/implementation/
Implementation status and reports:
- Overall implementation status
- Project structure
- Completion reports
- Technical summaries

## Quick Links

- **Getting Started:** [README.md](../README.md)
- **AI Agents:** [docs/features/AI_AGENTS.md](features/AI_AGENTS.md)
- **Marketplace:** [docs/features/AGENT_MARKETPLACE.md](features/AGENT_MARKETPLACE.md)
- **Chaining:** [docs/features/AGENT_CHAINING.md](features/AGENT_CHAINING.md)
- **Latest Phase:** [docs/phases/PHASE_14_SUMMARY.md](phases/PHASE_14_SUMMARY.md)
- **Project Structure:** [docs/implementation/PROJECT_STRUCTURE_REPORT.md](implementation/PROJECT_STRUCTURE_REPORT.md)

## Benefits of This Structure

✅ **Clean Root Directory** - Chỉ 4 file markdown ở root  
✅ **Organized by Category** - Dễ tìm kiếm documentation  
✅ **Scalable** - Dễ thêm documentation mới  
✅ **Standard Practice** - Theo chuẩn Maven/Java project  

## Adding New Documentation

**Phase documentation:**
```bash
# Add to docs/phases/
docs/phases/PHASE_XX_SUMMARY.md
```

**Feature documentation:**
```bash
# Add to docs/features/
docs/features/NEW_FEATURE.md
```

**Implementation reports:**
```bash
# Add to docs/implementation/
docs/implementation/NEW_REPORT.md
```

---

**Note:** Tất cả links trong README.md đã được cập nhật để trỏ đến vị trí mới.
