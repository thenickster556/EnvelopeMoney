# # AI Engineering Protocol

**Authoritative Standard for AI-Assisted Development**

---

# Purpose

This document defines the mandatory engineering workflow, coding standards, and quality expectations for all AI-assisted software development.

The goals are to:

* Preserve architecture integrity

* Minimize context drift

* Produce maintainable software

* Ensure high-quality documentation

* Keep project memory synchronized

* Produce code that is easy to understand and maintain

* Favor clarity over cleverness

This protocol applies to all programming languages unless a project explicitly overrides specific rules.

---

# Core Philosophy

The primary objective is **long-term maintainability**.

The AI should optimize for:

* Readability

* Maintainability

* Correctness

* Consistency

* Modularity

* Testability

The AI should **not** optimize for:

* Shortest code

* Clever implementations

* Code golf

* Unnecessary abstractions

* Language tricks

Future developers should be able to understand the code quickly without requiring the original author.

---

# 1. Load Project Context (Mandatory)

Before making any code changes, review the project's authoritative documentation.

## Minimum Recommended Project Memory

```text

docs/

    [README.md](http://README.md)

    PROJECT_[INDEX.md](http://INDEX.md)

    [ARCHITECTURE.md](http://ARCHITECTURE.md)

    DATA_[SCHEMA.md](http://SCHEMA.md)

    [FEATURES.md](http://FEATURES.md)

    USER_[FLOWS.md](http://FLOWS.md)

    DEVELOPMENT_[LOG.md](http://LOG.md)

state/

    TASK_STATE.json

prompts/

    codex_[rules.md](http://rules.md)

```

These documents define the intended system behavior and take precedence over assumptions.

If required documentation is missing or outdated:

**Stop.**

Request clarification before implementation.

Never invent:

* Architecture

* File layouts

* Data models

* APIs

* Workflows

---

# 2. Determine Change Scope

Classify the request before implementation.

## Minor Changes

Examples:

* Bug fixes

* UI adjustments

* Documentation

* Isolated enhancements

* Small refactors

Proceed with the standard workflow.

---

## Major Changes

Examples:

* New features

* Architectural changes

* New subsystems

* Major refactors

* Multiple interacting modules

Follow the **4-Prompt Development Method**.

### Prompt 1

Architecture Design

### Prompt 2

Project Skeleton

### Prompt 3

Module Generation

### Prompt 4

Integration & Review

Architecture should be approved before implementation begins.

---

# 3. Log Development Intent

Before writing production code:

Update the project development log.

Record:

* Purpose

* Motivation

* Systems affected

* Expected files

* Estimated impact

Update `TASK_STATE.json` with the planned work.

---

# 4. Implementation Precheck

Before coding, summarize:

* Affected modules

* Repositories

* Services

* APIs

* Data structures

* Database tables

* User flows

* Dependencies

* Expected files

* Risks

If the request conflicts with documented architecture:

**Stop and request clarification.**

---

# 5. Test-First Development (Mandatory)

Before implementation:

Create or update automated tests.

Tests should cover:

* Expected behavior

* Edge cases

* Invalid input

* Failure scenarios

* Regression scenarios

Run the tests.

New tests should fail before implementation begins.

Production code should only be written after failing tests verify the missing behavior.

---

# 6. Implement Minimal Solution

Implement only the code required to satisfy the tests.

Follow these principles:

* Small focused functions

* Descriptive naming

* Reusable logic

* Avoid duplication

* Maintain existing architecture

* Avoid unnecessary abstractions

* Avoid premature optimization

Do not introduce unrelated functionality.

---

# 7. Build & Verify

After implementation:

Build or compile the project.

Run:

* New tests

* Modified tests

* Regression tests

* Static analysis

* Linting (if configured)

If failures occur:

* Debug

* Fix

* Re-run

Repeat until all tests pass.

---

# 8. Safe Refactoring

After tests pass:

Improve code quality.

Possible improvements:

* Remove duplication

* Simplify logic

* Improve naming

* Improve modularity

* Improve maintainability

* Optimize performance when justified

Behavior must remain unchanged.

Run the complete test suite again after refactoring.

---

# 9. Documentation Standard

Documentation exists to help future developers understand **why** code exists and **how** it works.

Document:

* Modules

* Classes

* Interfaces

* Exported functions

* Public methods

* Complex private functions

Documentation should explain:

* Purpose

* Inputs

* Outputs

* Assumptions

* Side effects

* Edge cases

* Performance considerations (when relevant)

Avoid comments that simply restate the code.

Inline comments should only explain:

* Complex logic

* Algorithms

* Unusual business rules

* Workarounds

* Important implementation decisions

---

# 10. Update Project Memory

Synchronize project documentation with implementation.

Update as applicable:

* [README.md](http://README.md)

* PROJECT_[INDEX.md](http://INDEX.md)

* [ARCHITECTURE.md](http://ARCHITECTURE.md)

* DATA_[SCHEMA.md](http://SCHEMA.md)

* [FEATURES.md](http://FEATURES.md)

* USER_[FLOWS.md](http://FLOWS.md)

* DEVELOPMENT_[LOG.md](http://LOG.md)

* TASK_STATE.json

Documentation must accurately reflect the implementation.

---

# 11. Visual Validation (UI Changes)

For user-visible changes:

Provide:

* Before/after screenshots or mockups

* Visual descriptions

* Accessibility considerations

* Responsive behavior notes

Perform manual smoke testing of affected workflows.

---

# 12. Final Review

Before completion verify:

* No accidental scope creep

* No dead code

* No unused code

* No stray debugging

* No forgotten TODOs

* Edge cases covered

* Architecture preserved

* Security considered

* Performance acceptable

Ensure only intended files were modified.

---

# 13. Final Change Report

Provide:

* Summary

* Files changed

* Tests added or modified

* Documentation updated

* Verification completed

* Known limitations

* Future work (if applicable)

---

# 14. Commit Preparation

Stage:

* Source code

* Tests

* Documentation

Use Conventional Commits.

Examples:

* `feat:`

* `fix:`

* `docs:`

* `refactor:`

* `test:`

* `perf:`

* `build:`

* `chore:`

Example:

```text

feat: implement drag-and-drop playlist reordering

```

Commits should be staged for review before pushing.

---

# Engineering Style Guide

---

## Readability First

This project values readability over brevity.

Write code for the next developer, not the current developer.

Assume the next developer is a competent junior developer with little or no knowledge of the project.

If they cannot understand a function after reading it once, simplify it.

Readable code is preferred over shorter code.

---

## The 30-Second Rule

A competent junior developer should understand the purpose and general flow of any function within approximately **30 seconds**.

If not:

* Simplify the code

* Split the function

* Improve naming

* Add documentation

* Reduce nesting

* Remove clever constructs

---

## Code Simplicity

Prefer explicit code over concise code.

Favor code that is easy to debug and follow.

Break complicated logic into multiple understandable steps.

Introduce intermediate variables when they improve readability.

Avoid compressing multiple operations into a single expression.

Use multiple simple statements instead of one complex statement whenever practical.

---

## Discouraged Constructs

Avoid unless there is a compelling reason:

* Nested ternary operators

* Chained ternaries

* Dense one-line expressions

* Deeply nested conditionals

* Overly compressed loops

* Excessive method chaining

* Hidden side effects

* Clever language tricks

* Unnecessary recursion

* Overly complex lambda expressions

* Magic numbers

* Obscure regular expressions

If these constructs are necessary:

Document why they were chosen.

---

## Preferred Control Flow

Prefer:

* Explicit `if/else` statements

* Clear loops over condensed expressions

* Early returns instead of deeply nested logic

* Straightforward debugger-friendly execution flow

Avoid writing code solely because it is shorter or more elegant.

The simplest correct implementation should generally be preferred.

---

## Naming Standard

Names should immediately describe intent.

Prefer descriptive names over short names.

Avoid abbreviations unless they are universally recognized.

Good examples:

* `currentPlaylistIndex`

* `songRepository`

* `databaseMigrationService`

* `selectedVerseReference`

Avoid:

* `tmp`

* `obj`

* `str`

* `val`

* `repo`

* `foo`

* `bar`

Names should make the code self-explanatory.

---

## Naming Convention

The preferred naming convention is **camelCase**.

Apply camelCase consistently to:

* Variables

* Functions

* Methods

* Properties

* Fields

Use descriptive camelCase names whenever practical.

### Exception

If contributing to an existing project that consistently follows another naming convention, preserve that project's established style rather than introducing mixed conventions.

Consistency within a project is more important than strict adherence to a language's traditional style guide.

---

## Function Design

Functions should:

* Perform one responsibility

* Be easy to understand

* Have descriptive names

* Avoid excessive parameters

* Avoid unnecessary side effects

When a function becomes difficult to understand:

Split it into smaller functions.

---

## Architecture Preservation

Before implementing changes ask:

* Is it reusable?

* Is it testable?

* Is it documented?

* Is it maintainable?

* Does it preserve architecture?

* Would another developer understand it?

If any answer is **No**, redesign before implementation.

---

## AI Workflow Efficiency

Avoid micro-prompt development whenever practical.

Instead, batch related work into larger implementation tasks.

Preferred workflow:

1. Design architecture

2. Approve architecture

3. Generate project structure

4. Generate complete modules

5. Integrate

6. Review

This minimizes context drift and improves implementation quality.

---

# Completion Checklist

Before considering work complete verify:

* [ ] Project context loaded

* [ ] Architecture reviewed

* [ ] Change logged

* [ ] Implementation analyzed

* [ ] Tests written first

* [ ] Minimal implementation completed

* [ ] Project builds successfully

* [ ] All tests pass

* [ ] Safe refactoring completed

* [ ] Public code documented

* [ ] Complex logic documented

* [ ] Project documentation updated

* [ ] Project memory synchronized

* [ ] UI validated (if applicable)

* [ ] Final review completed

* [ ] Change report produced

* [ ] Commit prepared