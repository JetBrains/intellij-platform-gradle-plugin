# Architectural Audit Master Prompt

Conduct an evidence-backed architectural audit of the IntelliJ Platform Gradle Plugin repository.

## Inputs

- Repository: <repository path or URL>
- Baseline: <commit; resolve and record if unspecified>
- Requested scope: <components and review areas; use the scope below if unspecified>
- Previous findings: relevant earlier dated reports in `docs/audits/`, if any, and any explicitly supplied earlier reports; record their dates, audited revisions, and any missing reports or unknown provenance.
- Additional constraints: <explicit constraints, or "none known">

## Mission

Identify entrenched assumptions, cross-subsystem interactions, and obsolete compatibility machinery that warrant architectural decisions.

Optimize for consequential insights maintainers may have missed—not the number of findings, stylistic conformity, or novelty.

The output is a maintainer decision memo:

- What should change?
- What should remain, deliberately?
- What requires further validation?
- What evidence supports each decision?

## Scope

Start from current code, public contracts, tests, and documented behavior. Cover shipped functionality plus relevant tests, fixtures, CI, build logic, API checks, and documentation.

Within the requested scope, review correctness, public API and runtime compatibility, error handling, dependency usage, test gaps, performance, and maintainability. Record excluded and deferred areas rather than implying whole-repository coverage from a scoped audit.

Investigate history only when current-state analysis raises a specific question. Follow relevant commits, issues, PR discussions, regressions, and rejected alternatives. Do not conduct unrestricted historical archaeology.

Follow relevant Gradle, IntelliJ Platform, and dependency behavior as evidence; do not expand into an unrestricted audit of upstream projects.

## Compatibility and Principles

Use the minimum supported versions committed at the baseline. Do not assume a planned version increase.

Separate:

1. Recommendations compatible with current public contracts.
2. Future breaking-change proposals, with affected consumers, benefits, migration paths, and an explanation of insufficient compatible alternatives.

Favor Gradle-native lifecycle and dependency semantics, reproducibility, configuration-cache correctness, and observable, overridable behavior. Convenience is valuable when compatible with those principles.

Custom infrastructure and complexity are not defects by themselves. Require evidence of a violated invariant, concrete maintenance cost, meaningful risk, or useful simplification.

Investigate obsolete compatibility paths explicitly. An old version check does not establish obsolescence: distinguish build targets, verification targets, dependency metadata, descriptor formats, runtime behavior, diagnostics, and public API obligations.

## Permissions

By default, the audit phase is read-only. Running checks, saving reports, or implementing fixes requires explicit permission; an audit request alone authorizes a report, not remediation.

- Do not edit the checkout or create worktrees.
- Do not run Gradle, builds, tests, benchmarks, IDE processes, or reproducers.
- Do not download build artifacts, provision runtimes, or delete caches.
- Do not publish, open issues/PRs, or mutate remote state.
- Emit deliverables in the response; do not persist files without permission.

Read-only repository, history, issue, documentation, and source research is allowed. Check tool side effects: a tool described as "resolution" or "source inspection" may still run Gradle or download artifacts.

Document proposed experiments without executing them.

If the request also authorizes remediation, record the audit findings first, then treat implementation as a separate phase under [AGENTS.md](../../AGENTS.md). Preserve the original evidence and report the fixes and their verification separately.

## Execution

1. Record the audit date (including timezone), full baseline commit SHA, working-tree state (`git status --short`), relevant staged/unstaged changes and untracked files, requested scope, support constraints, available capabilities, and limitations. Distinguish committed code from local changes and preserve all existing user work. Record unknown provenance as unknown.
2. Inspect the architecture, build configuration, CI, tests, and relevant documentation. Map every in-scope subsystem, its responsibilities, public contracts, state, lifecycle boundaries, and interactions.
3. Build a risk-ranked investigation queue. Favor consequential questions involving shared state, boundary transitions, compatibility assumptions, hidden side effects, and weak validation.
4. Investigate selected questions deeply. Establish reachability and impact, check existing safeguards and tests, and seek counterevidence and legitimate design rationale before recommending change.
5. Consult history only to resolve specific questions.
6. Recheck every previous finding within scope against the current code. Cite its source report, retain its ID, and classify it as still present, verified resolved, not reproduced, or accepted tradeoff, with current evidence. List out-of-scope or blocked findings as not assessed; absence of evidence is not proof of resolution. Record missing previous reports explicitly. If no earlier reports are available, state that without creating a placeholder.
7. Reconcile cross-subsystem conclusions and conduct an adversarial review.
8. Produce the decision memo and supporting records, including reviewed areas with no actionable findings.

Proceed without review gates. Delegate bounded investigations and counterevidence reviews when useful. All delegates inherit these rules. The primary auditor verifies citations, reconciles contradictions, and owns the final conclusions. Agent agreement is not independent proof.

## Evidence

Every substantive finding must cite supporting code, commits, issues, documentation, or reproducible experiment records.

Use revision-pinned code locations and distinguish:

- Direct observations.
- Source-demonstrated consequences.
- Experimentally reproduced behavior.
- Historical reports.
- Inference and unresolved assumptions.

A source-demonstrated problem requires an established reachable path, violated contract or invariant, and consequence without unresolved runtime assumptions. Otherwise classify it as a hypothesis.

Do not claim performance, timing, concurrency, or platform-specific failures from suspicious code alone. Existing tests and historical reports establish only what their provenance supports.

Distinguish demonstrated problems, hypotheses, intentional tradeoffs, and simplification opportunities. Historical intent may remain unknown.

## Finding Dossiers

Include these fields for every finding:

- **Stable ID and title:** retain earlier `AUD-007`-style IDs; assign new, unused IDs without renumbering historical findings.
- **Category:** confirmed defect, technical debt, or concern requiring investigation. A concern remains unconfirmed until the evidence requirements above are met.
- **Severity:** critical, high, medium, or low, justified by impact and triggering conditions rather than uncertainty.
- **Confidence:** high, medium, or low, justified separately by the strength and limits of the evidence.
- **Location:** repository-relative path, symbol, and line range pinned to the audited revision. Label evidence from uncommitted changes and identify the relevant diff separately.
- **Evidence:** reachable code path and violated contract, an existing failing check or reproduction record, or an explicitly untested hypothesis. Include safeguards and counterevidence considered; distinguish a proposed experiment from one actually executed.
- **Impact and conditions:** affected consumers, configuration, versions, platforms, and concrete consequences. Technical debt needs a specific maintenance or testing cost, not a size or style preference.
- **Suggested next step and verification:** compatible fix or further investigation, relevant checks, and the observable result that would validate the fix. Mark checks not run.
- **Previous reference:** earlier finding ID, issue, or report, where applicable; otherwise state that the finding is new.

## Prioritization

Maintain separate queues:

- Immediate risks: demonstrated correctness, security, and compatibility risks.
- Architectural decisions: recurring maintenance cost, cross-subsystem leverage, simplification benefit, and migration cost.

Keep severity, confidence, and remediation priority separate. Group symptoms by root cause without concealing distinct mechanisms.

## Output

Produce:

1. Baseline and scope: date, full commit SHA, relevant working-tree changes, support constraints, permissions, and previous-report provenance.
2. Executive decision memo, explicitly allowing no actionable findings.
3. Evidence-backed finding dossiers using the required fields above.
4. Previous-finding reconciliation: ID, current status, current evidence, and any reason verification was blocked or deferred.
5. Deliberate-retention decisions and rejected hypotheses.
6. Obsolete-compatibility inventory.
7. Coverage and evidence ledger: components and files inspected, review areas covered (including those with no actionable findings), and exact commands actually run with their results. State explicitly when builds, tests, or reproducers were not run.
8. Unexecuted validation queue, capability gaps, excluded or deferred areas, and limits on the conclusions.

Preserve earlier dated reports as historical evidence; a missing or unverified report is not evidence that the project is issue-free. Emit the new report in the response unless saving it is explicitly authorized. When authorized, save it as `docs/audits/YYYY-MM-DD-<short-sha>.md`, using the audit date and audited commit's short SHA, and record its full baseline. Create the directory with the first saved report; do not create placeholder reports. If the filename already exists, append a numeric suffix such as `-2` rather than replacing an earlier report. The procedure stays in this file, not in the report or [AGENTS.md](../../AGENTS.md).

## Stopping

Do not target a finding count. Stop an investigation when resolved, blocked by unavailable validation, or further read-only research stops changing the decision. Stop the overall audit after the coverage pass, risk-ranked investigations, and counterevidence review, explicitly accounting for deferred questions. Do not imply exhaustive assurance.
