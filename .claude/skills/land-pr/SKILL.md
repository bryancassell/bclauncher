---
name: land-pr
description: Make one PR for a GitHub issue, from a new branch to ready for the developer to merge. Implement it, open the PR, run two xhigh /code-review rounds that post their findings to the PR, then hand it to the developer once CI passes, asking the developer every question that needs their call. The developer gives it the final review and merges it.
argument-hint: <issue-number>
disable-model-invocation: true
---

# One PR for an issue

The issue number, N below, is `$ARGUMENTS`. If that isn't a single issue number, ask for one. A leading `#` can be dropped without asking.

Make one PR for issue #N, from a new branch to ready to merge, following CLAUDE.md's Git Development Workflow and Claude Code Behaviors. An issue can take more than one PR; this covers one. The developer gives the PR its final review and chooses when to merge it, so stop once it's ready.

## When to ask

Ask with AskUserQuestion, recommended option first, whenever the answer is the developer's to give:

- The issue is unclear, or what it expects conflicts with PRD.md or ARCHITECTURE.md.
- The issue needs more than one PR: which part this one covers.
- Two or more reasonable approaches differ in a way the developer would care about.
- A change would add or change a row in PRD.md's Design decisions, or a decision in ARCHITECTURE.md, or make either file longer.
- You'd decline a review finding about correctness, or fixing one would change behavior the issue didn't ask about.
- A test or CI failure isn't clearly caused by this change.

Don't ask what the code, the docs or a convention already answers. Ask when the question comes up, not in a batch at the end. Record each answer in the PR description, in the section it affects: for example, the approach chosen and why under `## What`.

## How to ask

Before each AskUserQuestion call, write the question up in a message: first a brief the developer can decide from without reading further, then the evidence it rests on. If one call asks more than one question, give each its own write-up under its `header`.

The brief, in this order:

1. **Decision.** What you're asking, in a sentence of plain words. If the options share most of the change, say what they share and where they differ.
2. **Why it matters.** What the answer shapes: what people see or hear, the code and tests, ARCHITECTURE.md or PRD.md, and planned work that builds on it, such as an open issue. Then how hard it would be to change later.
3. **Options.** Name each by what it does, and use that name as its label in the dialog. Under each, its pros and cons, one line apiece, each citing the evidence it rests on by number.
4. **Recommendation.** The option and the reason that decides it. Favor the option that follows the documented best practice and the project's existing patterns over one that changes less code. Then say what would make another option the better pick.

Then the **Evidence**, numbered. Look for all three kinds:

- **Previous decisions.** Rows of PRD.md's Design decisions and ARCHITECTURE.md's Decisions table, quoted, with their sections and issue links. Earlier issues and PRs where the same choice came up, found with `gh issue list --state all --search`, `gh pr list --state all --search` and `git log --grep`, with what was chosen and why. Answers the developer already gave on this PR. If you found none, one item says what you searched for.
- **Existing patterns.** How the code already handles the same case, as `path:line` with a short excerpt, how many places follow it, and any place that does it differently.
- **Best practices.** What the Android, Kotlin, Compose or library documentation recommends, with a link to the page, and where the project already departs from it and why. If it's from memory rather than a page you read, say so.

Leave out what the brief doesn't cite. How an option would be built, such as the files it touches and its signatures, goes in the evidence or the option's `preview`, not the brief. For example, #342's logging question, shortened:

```markdown
**Decision:** How warnings reach Android's log once the logging call is in shared code.

**Why it matters:** It decides whether about 14 ViewModels take a logger parameter [2]. Switching later touches the same files.

**Options**
1. **Inject a logger with Koin, like the clock**
   - Pro: follows Kotlin's advice [3] and the `ZonedClock` pattern [1]
   - Con: about 35 files change for 7 log lines [2]
2. **A global writer the app sets at startup**
   - Pro: 7 one-line changes [2]
   - Con: a platform that forgets to set it loses warnings silently

**Recommendation: 1**, since it follows the docs and the pattern already in place. Pick 2 if a small diff matters more.

**Evidence**
1. `di/AppModule.kt:82`: `ZonedClock` is injected so tests can pass their own. 10 files take it.
2. …
```

Fit the write-up to the question. One with an obvious answer and no lasting effect, such as deleting a branch that's already merged, needs only the decision and the recommendation.

For a review finding, the decision says what the finding claims. The brief says what you checked: whether the state it describes can happen, what fixing it would change, and why you'd decline it. The evidence quotes its whole comment as posted, not a summary: the heading, the body and any suggested change, with the file, the line and the comment's link, from `gh api 'repos/{owner}/{repo}/pulls/<PR number>/comments'`. A round 1 finding on the same code, and the reply to it, is a previous decision.

For a test or CI failure, the evidence quotes the failing output, links the run, and says whether the test passes on `main`.

In the AskUserQuestion call, the question is the decision's sentence, and each option's description gives its main pro and con. Use an option's `preview` to show the code it would leave, such as a finding's suggested change beside the code you'd keep.

Quote and give lines only from what you read in this session. A wrong quote misleads more than none.

## 1. Read the issue

1. `gh issue view N --comments`. Look for work already started: `gh pr list --state all --search '"(#N)" in:title'` for its PRs, and `git branch --list '*/N-*'` for local branches. If a PR is still open or a branch exists, ask whether to continue it. To continue, check out its branch and pick up at the first step not yet done: an open PR skips step 3, and its description's review sections show which rounds have run.
2. Read the tables of contents of ARCHITECTURE.md and PRD.md, then only the sections the issue touches.
3. Find the cause. If what's left of the issue is too much for one PR, propose a split and ask which part this PR covers.
4. Ask the questions it raises before writing code.

## 2. Implement

1. `git status --porcelain` prints nothing. If it prints anything, ask what to do with those changes rather than carrying them onto the new branch.
2. `git fetch origin`, then `git switch --no-track -c fix/N-<short-slug> origin/main` for a bug, or `feature/N-<short-slug>` otherwise. Branch from `origin/main` rather than switching to `main`: in a git worktree, the main checkout has `main` checked out, and git won't check out a branch in two places.
3. Write a test that fails for the issue's reason, then make it pass. Update PRD.md and ARCHITECTURE.md in the same change when a decision changes, following CLAUDE.md's "What goes where": the cause of a bug and how the fix works go in a comment at the fix, not in either document. Check each paragraph you add to either file against it, and remove what your change makes redundant.
4. `./gradlew build` passes. CI's Build job runs the same command: unit tests, the coverage check, lint and Spotless. When the change touches `app/src/androidTest`, `./gradlew pixel6Api37DebugAndroidTest` passes too, as in CI's Instrumented tests job.
5. The coverage check only holds each class to 80%. If the change lowers coverage anyway, say so and why under `## Tests`, with numbers from `./gradlew jacocoDebugCoverageReport`.
6. For a change people can see or hear, check it on the emulator through a subagent that reports back in text. Also do the checks docs/toolchain.md asks for by hand: "Checking a release build" after adding or updating AGP, a library, a keep rule or code that uses reflection, and "Checking backup and restore" after changing the backup rules or where data is stored.

## 3. Open the PR

1. Commit, `git push -u origin HEAD`, then `gh pr create`. If the change makes ARCHITECTURE.md or PRD.md longer and the developer agreed, add `--label grows-docs`: CI's Doc growth check fails without it.
2. Title: what now works, in plain words, ending in `(#N)`. For example, "Keep Badges' search field above the keyboard in landscape (#308)".
3. Body, as in recent PRs (`gh pr view 314` is a model): `Closes #N.` if this PR finishes the issue, or `Part of #N.` and what's left if it doesn't. Then what was wrong, and `## Cause`, `## What`, `## Tests` and, for the checks in step 2.6, `## Checked on the emulator`. The squash commit takes the PR's title and body, so write them for `main`'s history.
4. If ARCHITECTURE.md or PRD.md changed, end `## What` with how many words each gained or lost (`wc -w`). If one grows later, such as from a review fix, and the developer agrees, `gh pr edit <PR number> --add-label grows-docs`.

## 4. Two review rounds

Do this twice. Round 2 reviews the PR with round 1's fixes pushed.

1. Run the code-review skill with `xhigh --comment <PR number>`. It posts its findings to the PR.
2. Judge each finding by the state it describes, not only by its example: disproving the example doesn't make the state unreachable. Fix it, or decline it with a reason. Ask before declining a correctness finding.
3. `./gradlew build` passes, and the checks in step 2.6 are redone for anything the fixes changed. Commit and push.
4. Edit the PR description in place. Add `## After the /code-review` (round 1) or `## After the second /code-review` (round 2), with lists headed "Fixed:", "Not changed, by agreement:" and "Not changed:", each item with its reason.

## 5. Hand it to the developer

The developer gives the PR its final review and chooses when to merge it. Don't merge it, turn on auto-merge or delete its branch, even once CI passes.

1. Wait for CI on the last push with `gh pr checks <PR number> --watch`. Fix a failure this change caused, and ask about one it didn't clearly cause.
2. Tell the developer the PR is ready to review: its link, whether CI passed, and anything that deserves a close look, such as the findings under "Not changed:".
