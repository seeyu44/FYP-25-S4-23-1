# Deepfake Speech Detection (Android)

This repository contains an Android app (Kotlin + Compose) and an ML training skeleton (Python) for detecting deepfake speech during calls. The code follows a Boundary–Control–Entity (BCE) structure.

## Layout

- app/src/main/java/com/example/fyp_25_s4_23/
  - presentation/handlers – OS-boundary services (CallMonitorService, CallScreeningServiceImpl)
  - control/controllers, control/usecases – orchestration
  - domain/entities – core models
  - data/repositories – simple placeholder repo
  - ml – ModelRunner + ModelConfig stubs
  - util – small helpers
- app/src/main/assets – put model artifacts here (e.g., model.tflite)
- ml/training – Python code for training and export
- ml/model – exported model artifacts
- docs – architecture, privacy, threat model
- scripts – helper scripts (e.g., sync_model.ps1)

## Getting Started (Android)

1. Open the root folder in Android Studio.
2. Build and run the app (the current UI is a simple Compose screen).
3. To test the monitoring service, wire UI buttons to Start/Stop use cases or start the service from `MainActivity` (requires RECORD_AUDIO permission).

Permissions declared:
- RECORD_AUDIO, READ_PHONE_STATE, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE

Note: Access to in-call audio is restricted on Android 10+. Consider role-based call screening or user-initiated monitoring where lawful.

## ML Artifacts

Train or export your model into `ml/model/`, then run:

```
powershell -ExecutionPolicy Bypass -File .\scripts\sync_model.ps1
```

This copies artifacts to `app/src/main/assets/model/`.

## Next Steps

- Implement `ModelRunner` with TFLite or ONNX Runtime Mobile.
- Add feature extraction (AudioRecord + MFCC/log-mel) and connect to the service.
- Add Room/DataStore and repositories for analytics and settings.
- Implement dashboard screens (analytics, settings, live alert) under `presentation/ui`.

