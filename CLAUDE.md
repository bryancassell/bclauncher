The product requirements document for this project is in PRD.md

The architecture design for this project is in ARCHITECTURE.md. Read its table of contents first, then only the sections you need. Follow it for new features, and update it in the same PR when a design decision changes.

## What goes where

- **ARCHITECTURE.md:** what someone changing a different part of the app must know. That means the technical decisions and conventions that more than one place follows, each with its reason in a sentence or two. Each row of its Decisions table is one line that links to its section.
- **PRD.md's Design decisions:** choices about how the app looks and behaves. Each row gives the choice in a sentence or two, the main reason, and issue links.
- **A comment at the code:** anything that concerns one class, one function or one bug. That includes how it works, why it's built that way, alternatives that were tried, library internals and the versions they were checked against, and the issue link. A comment gives its own reason rather than sending the reader to ARCHITECTURE.md or PRD.md for it. It names their section or row only for a convention or choice they set for more than this code, such as "(ARCHITECTURE.md, Language and layout direction)".
- **docs/toolchain.md:** build, CI, signing and release process. **docs/catalog.md:** the catalog's format and how to write it.
- **Issues and PRs:** how a bug was found and fixed, and whether an issue is still open.

Before adding to ARCHITECTURE.md or PRD.md, check that the text isn't already in a comment, and remove what the addition makes redundant. A PR that makes either file longer needs the developer's agreement and the `grows-docs` label; CI's Doc growth check fails without it.

# Claude Code Workflow

## Git Development Workflow

Use this workflow when you start working on a github issue.

1. Create a new local git feature branch for this issue, branched from the latest `main` branch.
2. Ask the human developer when there are any significant questions or ambiguities to resolve.
3. Being working on the issue after the questions are resolved, but continue to ask if new questions or ambiguities arise.
4. When work is complete, commit all changes and create a PR.

# Claude Code Behaviors

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

Simplicity is about what the code does, not the size of the diff. When approaches differ, prefer the one that follows best practices and the project's existing patterns, even if it changes more files.

## 3. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## 4. Following Android Best Practices

- Implement all solutions using modern Android best practices, regardless of the current project minSdk setting.
- If your solution is not compatible with the current minSdk setting, research potential alternatives and ask the human developer for next steps.

## 5. Testing Best Practices

- Every class containing logic (ViewModels, use cases, repositories, mappers) should have a corresponding unit test.
- Testing line coverage for all new code should be at least 80%.
- Testing line coverage in a touched module should not decrease; if it does, explain why.
- Tests must verify meaningful behavior, not just exercise lines.
- Fakes are preferred over mocks when possible. Mocks couple tests to implementation details, while fakes test behavior.
- UI tests should be written to validate each distinct UI state (e.g., loading, content, empty, error) and each user interaction.
- Screenshot tests should be used selectively, mainly for visual states that are difficult to assert on semantically.
- When a test fails, fix the code or explain why the expectation is wrong. Never delete or loosen assertions, skip tests, or re-record screenshot baselines to get tests passing.
- Prefer local tests; use instrumented tests only for behavior that requires a real Android runtime.

## 6. Keeping Context Small

- Check the app on an emulator through a subagent that reports back in text. When viewing a screenshot directly, crop it to the area being checked or scale it down first.
- Read only the part of a file you need: find line numbers with grep, then print that range. Send broad searches to the Explore agent.
- Edit PR descriptions and docs in place rather than rewriting them, and check `git diff --stat` before printing a full diff.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
