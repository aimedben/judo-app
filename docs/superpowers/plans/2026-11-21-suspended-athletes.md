# Suspended Athletes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the existing `SUSPENDU` member status into every UI surface (pointage, athletes list, SMS center, statistics, attendance calendar, dashboard, member detail, categories) so a suspended athlete is never counted absent and is visually distinct (red card, black calendar days).

**Architecture:** No schema change — `Member.status` already exists and is synced by `SupabaseSyncService`. All work is UI-layer filtering, coloring, and counting logic keyed on `member.isSuspended`.

**Tech Stack:** Kotlin, Jetpack Compose, Room (no migration), Supabase sync (unchanged).

**Spec:** `docs/superpowers/specs/2026-11-21-suspended-athletes-design.md`

## Global Constraints
- Suspension has NO date range (option 2 chosen). In the attendance calendar, all training days of a suspended athlete are black.
- Suspended athletes appear in the pointage absent list with a "Suspendu" badge.
- `JudoRed` is the color for suspended surfaces; `Color.Black` for calendar days.
- No new database columns, no Supabase schema changes.
- Build must still compile with the existing gradle invocation (offline, JDK 21).

## Review Focus
- A suspended athlete must never be recorded as an absence (validation + alerts + monthly count).
- The pointage absent count and SMS recipients must exclude suspended from real absences while still listing them with a badge.
- Calendar black days must not be counted as absences in `monthlyAbsentSessions`.
- Status must survive a cloud pull (no field dropped in `pullMembers`).

---

### Task 1: Athletes list — suspended filter + red card

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/MembersScreens.kt` (enum `MemberQuickFilter`, `filteredMembers`, filter chips, `MemberListRow`)

**Interfaces:**
- Produces: `MemberQuickFilter.SUSPENDED` value; `filteredMembers` handles it.

- [ ] **Step 1:** Add `SUSPENDED` to `MemberQuickFilter` enum.
- [ ] **Step 2:** In `filteredMembers`, add branch `MemberQuickFilter.SUSPENDED -> m.isSuspended`.
- [ ] **Step 3:** Add a `FilterChip` "Suspendus (N)" (toggle, red `JudoRed` when selected) after the "Blessés" chip, with testTag `members_filter_suspended`.
- [ ] **Step 4:** In the empty-state `when`, add `MemberQuickFilter.SUSPENDED -> "Aucun athlète suspendu actuellement."`.
- [ ] **Step 5:** In `MemberListRow`, add `isSuspended` handling: `containerColor = JudoRed.copy(alpha = 0.16f)` (red tint) when suspended, before the injured branch; border red; text color dark red. Keep existing badge.
- [ ] **Step 6:** Verify build compiles: `gradle :app:compileDebugKotlinAndroid` (or release). Expected: BUILD SUCCESSFUL.

### Task 2: Attendance calendar — black days for suspended

**Files:**
- Modify: `app/src/main/java/com/example/ui/components/MemberAttendanceCalendar.kt` (`CalendarDayData`, `CalendarDayCell`, legend, monthly counting)

**Interfaces:**
- Consumes: `member.isSuspended`.

- [ ] **Step 1:** Add `isSuspendedPeriod: Boolean = false` to `CalendarDayData`.
- [ ] **Step 2:** In the day-mapping loop, set `isSuspendedPeriod = member.isSuspended` for every day (option 2: whole month).
- [ ] **Step 3:** In monthly counting, change condition to `!isInjuredDay && !isSuspendedPeriod` so suspended days are excluded from `monthlyTotalTrainingSessions`/absent count.
- [ ] **Step 4:** In `CalendarDayCell` `when`, add a branch (before training-day-absent) for `dayData.isSuspendedPeriod && !dayData.isPresent`: cellColor = Color.Black, contentColor = Color.White, badgeIcon = null (or a "S" badge).
- [ ] **Step 5:** Add legend entry "Suspendu" with a black indicator.
- [ ] **Step 6:** Verify build compiles. Expected: BUILD SUCCESSFUL.

### Task 3: Pointage — badge + exclude from recorded absences + session SMS

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/CoachSessionPointageScreen.kt` (absent list items, `StatAthletesDialog`, validation, SMS recipients)

**Interfaces:**
- Consumes: `Member.isSuspended`.

- [ ] **Step 1:** In the manual check-in absent list item, when `member.isSuspended`, show a red "Suspendu" badge next to the name.
- [ ] **Step 2:** In `StatAthletesDialog` absent row, show "Suspendu" badge when `member.isSuspended`.
- [ ] **Step 3:** At validation ("Valider et enregistrer"), filter suspended members out of the recorded absences (only record absences for non-suspended non-present members).
- [ ] **Step 4:** In session SMS recipients, exclude suspended members (same as injured).
- [ ] **Step 5:** Verify build compiles. Expected: BUILD SUCCESSFUL.

### Task 4: SMS center — suspended mode

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/AdminSmsCenterScreen.kt` (`SmsMode`, `memberPool`, filter chips)

- [ ] **Step 1:** Add `SUSPENDUS` to `SmsMode` enum.
- [ ] **Step 2:** In `memberPool`, handle `SmsMode.SUSPENDUS -> allMembers.filter { it.isSuspended }`.
- [ ] **Step 3:** In the final `pool.filter { ... }`, make the suspended exclusion conditional: only exclude suspended when mode != SUSPENDUS (i.e., keep `it.status == "ACTIF" && !it.isInjured` for other modes; for SUSPENDUS keep all suspended).
- [ ] **Step 4:** Add a `SmsFilterChip` "Suspendus" with testTag `sms_filter_suspended`.
- [ ] **Step 5:** Verify build compiles. Expected: BUILD SUCCESSFUL.

### Task 5: Statistics — suspended filter mode

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/StatFilterModels.kt` (`StatFilterMode`)
- Modify: `app/src/main/java/com/example/ui/screens/AdminStatsScreens.kt` (`displayedMembers`, filter chips)

- [ ] **Step 1:** Add `SUSPENDUS("Suspendus", "Judokas suspendus", JudoRed)` to `StatFilterMode`.
- [ ] **Step 2:** In `displayedMembers`, add branch `StatFilterMode.SUSPENDUS -> allMembers.filter { it.isSuspended }`.
- [ ] **Step 3:** Add a `FilterChip` for the new mode in the stats filter row.
- [ ] **Step 4:** Verify build compiles. Expected: BUILD SUCCESSFUL.

### Task 6: Dashboard — suspended counter

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/AdminDashboardScreen.kt`

- [ ] **Step 1:** Add `val suspendedCount = allMembers.count { it.isSuspended }`.
- [ ] **Step 2:** Add a hero stat / counter for suspended (next to injured count) in the dashboard header stats.
- [ ] **Step 3:** Verify build compiles. Expected: BUILD SUCCESSFUL.

### Task 7: Member detail — suspended badge

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/MembersScreens.kt` (`MemberBeltHeader`)

- [ ] **Step 1:** In the badge row (BeltBadge + GroupBadge), add a red "Suspendu" Surface badge when `member.isSuspended`.
- [ ] **Step 2:** Verify build compiles. Expected: BUILD SUCCESSFUL.

### Task 8: Categories — suspended count per category

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/AdminCategoriesScreen.kt`

- [ ] **Step 1:** Where `activeMembers` is computed per category, also compute `suspendedMembers = membersInCat.count { it.isSuspended }` and display it in the category detail.
- [ ] **Step 2:** Verify build compiles. Expected: BUILD SUCCESSFUL.

### Task 9: Supabase pull verification

**Files:**
- Modify (if needed): `app/src/main/java/com/example/data/sync/SupabaseSyncService.kt` (`pullMembers`)

- [ ] **Step 1:** Inspect `pullMembers` to confirm `status` field is mapped from the cloud row into `Member`. If missing, add mapping.
- [ ] **Step 2:** Verify build compiles. Expected: BUILD SUCCESSFUL.

### Task 10: Final integration build + device smoke test

- [ ] **Step 1:** Run full release build (`assembleRelease`) with the documented env. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2:** Install APK on device `e25ffc230521`, open app, verify: suspended card red, filter works, agenda days black, pointage shows badge, SMS center suspended filter, stats suspended filter, dashboard counter.
- [ ] **Step 3:** Commit all changes with message "feat: suspended athlete status across all surfaces".
