# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MacroMate is a native Android app (Kotlin + Jetpack Compose) that acts as an AI-powered nutritional concierge integrated with Swiggy. Users onboard with physiological goals, then chat with a ReAct agent that searches restaurants, estimates macros, and ranks dishes against the user's targets.

## Build & Test Commands

```bash
# Debug build
./gradlew :app:assembleDebug

# Run unit tests
./gradlew :app:testDebugUnitTest

# Run instrumented tests (requires device/emulator)
./gradlew :app:connectedAndroidTest

# Screenshot tests (Roborazzi)
./gradlew :app:recordRoborazziDebug    # record baseline
./gradlew :app:verifyRoborazziDebug   # verify against baseline

# Full build
./gradlew :app:build

# Release APK
./gradlew :app:assembleRelease
```

## API Keys

Copy `.env.example` to `.env` and set `GEMINI_API_KEY`. The Secrets Gradle Plugin injects it as `BuildConfig.GEMINI_API_KEY` at compile time. If unset or left as placeholder, the app falls back to offline mock data automatically.

## Architecture

**Pattern:** MVVM + Repository + ReAct Agent

```
UI Screens (Compose)
    ↓
MacroMateViewModel        ← single ViewModel, StateFlow-based state
    ↓
MacroMateRepository       ← thin wrapper over DAO + agent + clients
    ↙          ↘
Room DB        MacroMateAgent + API clients
```

### Key Components

**`MacroMateAgent`** (`data/repository/MacroMateAgent.kt`) — The core intelligence. Implements a three-tool ReAct loop:
1. `searchRestaurants(query)` — delegates to SwiggyClient
2. `getMenuAndEstimateMacros(restaurantId)` — fetches menu + parses macros
3. Scoring: `0.5×ProteinMatch + 0.3×CalorieMatch + 0.2×PriceMatch`, with dietary filters and +0.05 boost for matching cuisine preferences

**`SwiggyClient`** (`data/repository/SwiggyClient.kt`) — Facade over restaurant data: delegates to the live Swiggy MCP server when the user has linked their account, otherwise falls back to the built-in offline mock (4 restaurants with nutritional data).

**`SwiggyMcpClient`** (`data/repository/SwiggyMcpClient.kt`) — Real Swiggy MCP integration (https://mcp.swiggy.com/food). OAuth 2.1 + PKCE with dynamic client registration; the redirect is caught on a localhost loopback socket. Speaks MCP streamable HTTP (JSON-RPC, SSE-aware). Uses `get_addresses`, `search_restaurants`, and `get_restaurant_menu`; menu macros are estimated in one batched Gemini call (keyword heuristic if no API key). Connect/disconnect lives in ProfileScreen. Tokens last ~5 days, no refresh flow in Swiggy v1.0 — users must reconnect after expiry.

**`RestGeminiClient`** (`data/repository/RestGeminiClient.kt`) — HTTP client for Gemini 3.5 Flash. Falls back to local simulation if API key is unavailable.

**`MacroMateViewModel`** (`ui/viewmodel/MacroMateViewModel.kt`) — Single ViewModel driving all screens. Handles auth, profile (with automatic macro calculation), cart, chat messages, and AI engine selection (Gemini vs Claude).

**Room DB** (`data/local/`) — Version 4 with real migrations (`MIGRATION_2_3`, `MIGRATION_3_4`); only v1 may be wiped. Schemas are exported to `app/schemas/` and verified by `DatabaseMigrationTest`. Entities: `UserAccount`, `UserProfile`, `MacroLog`, `OrderHistory`, `DayRecord` (per-user daily target snapshot + what the user said about the day), `WeightLog`, `OutsideSpendLog` (outside orders, Swiggy fees, month reconcile). Never add a column without a migration.

**Dietary Log & Monthly Budget** — rules live in pure Kotlin under `domain/` (no Android imports, fully unit-tested): `schedule/MealScheduleEngine` (meal slots, protein spread, workout/pre-sleep timing), `analytics/DayAnalytics` (a day with no logs is *unknown*, never 0 kcal; coverage vs adherence; capped weekly carry-over), `analytics/SafetyRules`, `analytics/WeightTrend`, `budget/BudgetEngine` (safe daily allowance, spend derived from stored rows). `DietaryLogViewModel` wires Room flows into them; `MacroMateViewModel` only passes the daily allowance to the agent (`setMealBudget`) — with no budget set, agent pricing is unchanged. Offline meal reminders: `notify/MealReminders` (AlarmManager + notification).

### Screens (7 total)
- `AuthScreen` — login/signup
- `OnboardingScreen` — 4-step wizard collecting bio, goals, allergies, cuisines
- `ChatScreen` — ReAct agent chat; displays thinking steps
- `TrackerScreen` — Canvas progress arcs + 7-day macro history chart
- `CartScreen` — Quantity controls, macro totals, Swiggy deep-link
- `DietaryLogScreen` — Meal schedule for the day, missed-day prompt, water/food groups, analytics, budget, weight
- `ProfileScreen` — Live profile editing, monthly food budget, AI engine selector

## Module Structure

Single-module app (`com.example` namespace). All source under `app/src/main/java/com/example/`:
- `ui/screens/` — Composable screens
- `ui/viewmodel/` — MacroMateViewModel
- `ui/theme/` — Material3 "Neon Emerald & Slate" theme (Color.kt, Type.kt, Theme.kt)
- `data/model/Models.kt` — All data classes
- `data/local/` — Room database + DAO
- `data/repository/` — Repository, Agent, SwiggyClient, RestGeminiClient

## Dependency Management

All versions centralized in `gradle/libs.versions.toml`. Key versions:
- Kotlin 2.2.10 + KSP 2.3.5
- Compose BOM 2024.09 + Material3
- Room 2.7.0 (uses KSP for annotation processing)
- Retrofit 2.12.0 + Moshi 1.15.2
- Robolectric 4.16.1, Roborazzi 1.59.0
