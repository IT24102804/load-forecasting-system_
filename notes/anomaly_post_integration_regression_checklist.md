# Anomaly Post-Integration Regression Checklist

Use this after merging any new AI component into the main system.

## Services
- Start weather model on `5000`
- Start load model on `5001`
- Start anomaly model on `5002`
- Start generation mix model on `5003`
- Start Spring Boot app on `8080`

## Verified UI Demo Cases

### Normal Case
- Timestamp: `2026-04-13T14:00:00`
- Temperature: `28`
- Humidity: `80`
- Public Event: `1`
- Expected load: about `1205.84`
- Expected anomaly result: `NORMAL`

### High Anomaly Case
- Timestamp: `2026-04-13T14:00:00`
- Temperature: `30`
- Humidity: `80`
- Public Event: `0`
- Expected load: about `1200.75`
- Expected anomaly result: `HIGH`

## Admin Checks
- Open `/anomaly`
- Confirm the recent anomaly table renders `Confidence`
- Confirm list and detail pages show saved confidence for new anomaly records
- Confirm acknowledge and resolve actions still work

## Automated Regression
- Run `.\gradlew.bat test`
- Confirm these tests still pass:
  - `ForecastAnomalySystemFlowTest`
  - `AnomalyUiRegressionSystemFlowTest`
  - `AdminAnomalyLifecycleSystemFlowTest`

## Backup Demo Path
- If the UI behaves unexpectedly, use the direct local API demo path:
  - get load from `5001`
  - send predicted load plus context to `5002`
