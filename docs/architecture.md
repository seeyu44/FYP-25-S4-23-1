# Architecture Overview

This app follows the Boundary–Control–Entity (BCE) pattern:

- Boundary: presentation/UI and OS integrations (handlers/services).
- Control: orchestrates use cases and service flows.
- Entity: core domain models and rules.

Modules (single-module project) use packages to separate layers:

- presentation/handlers – system listeners, services.
- control/controllers, control/usecases – orchestration.
- domain/entities – business models.
- data/repositories – persistence adapters.
- ml – on-device runtime wrapper and configuration.
- util – shared utilities.

ML training lives outside the app under `ml/training`. Exported models go into `ml/model` and are copied to `app/src/main/assets`.

