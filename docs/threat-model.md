# Threat Model (High-Level)

- Assets: user identity, contacts, call metadata, model integrity, alerts.
- Adversaries: impersonators using deepfake speech; malicious apps trying to access audio or model config; network observers if sync is enabled.
- Risks: false negatives (missed deepfakes), false positives (alarm fatigue), unauthorized data access.
- Controls: on-device inference, minimized permissions, secure storage for settings, signed model artifacts, explicit user consent.

