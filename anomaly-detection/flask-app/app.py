from flask import Flask, request, jsonify
from flask_cors import CORS
import json
import joblib
import numpy as np
import os
import pandas as pd

app = Flask(__name__)
CORS(app)

model = None
scaler = None
feature_order = None
model_metadata = {}
selected_model_name = "unknown"

DEFAULT_FEATURE_ORDER = ["load", "temp", "humidity", "hour", "day", "month", "event", "season"]
DAY_NAMES = {
    1: "Monday",
    2: "Tuesday",
    3: "Wednesday",
    4: "Thursday",
    5: "Friday",
    6: "Saturday",
    7: "Sunday",
    0: "Sunday",
}


@app.route("/anomaly_feedback", methods=["POST"])
def anomaly_feedback():
    data = request.json or {}
    print(f"Feedback received for prediction {data.get('prediction_id')}: User Agreed? {data.get('user_agreed')}")
    return jsonify({"status": "success", "message": "Feedback logged in Python!"}), 200


def load_json_file(file_path: str) -> dict:
    if not os.path.exists(file_path):
        return {}
    with open(file_path, "r", encoding="utf-8") as file:
        return json.load(file)


def load_model():
    global model, scaler, feature_order, model_metadata, selected_model_name

    base_dir = os.path.dirname(os.path.abspath(__file__))
    model_path = os.path.join(base_dir, "model.pkl")
    scaler_path = os.path.join(base_dir, "scaler.pkl")
    metadata_path = os.path.join(base_dir, "model_meta.json")

    model_metadata = load_json_file(metadata_path)
    if model_metadata:
        print("Loaded model metadata.")

    if os.path.exists(model_path):
        model = joblib.load(model_path)
        print("Model loaded.")
    else:
        print("Model file not found.")

    if os.path.exists(scaler_path):
        scaler = joblib.load(scaler_path)
        print("Scaler loaded.")
    else:
        print("Scaler file not found.")

    feature_order = model_metadata.get("feature_order") or list(getattr(scaler, "feature_names_in_", [])) or DEFAULT_FEATURE_ORDER
    selected_model_name = model_metadata.get("selected_model") or type(model).__name__

    print(f"Selected model: {selected_model_name}")
    print(f"Expecting features: {feature_order}")


def build_feature_frame(payload: dict) -> pd.DataFrame:
    row = {}
    for feature in feature_order:
        value = payload.get(feature, 0)
        if feature in {"hour", "day", "month", "event", "season"}:
            row[feature] = int(value)
        else:
            row[feature] = float(value)
    return pd.DataFrame([row], columns=feature_order)


def calculate_confidence(anomaly_score: float, decision_score: float | None) -> float:
    base_score = abs(decision_score) if decision_score is not None else abs(anomaly_score)
    confidence = 1.0 / (1.0 + np.exp(-base_score))
    return float(np.clip(confidence, 0.5, 0.999))


def classify_severity(anomaly_margin: float) -> str:
    if anomaly_margin >= 1.5:
        return "HIGH"
    if anomaly_margin >= 0.5:
        return "MEDIUM"
    return "LOW"


def build_reason(data: dict, severity: str, is_anomaly: bool) -> str:
    if not is_anomaly:
        return "Load behavior matches historical patterns."

    try:
        day_num = int(data.get("day", 1))
    except (ValueError, TypeError):
        day_num = 1

    current_day = DAY_NAMES.get(day_num, "this day")
    load_value = float(data.get("load", 0.0))
    temp_value = float(data.get("temp", 0.0))
    hour_value = int(data.get("hour", 0))

    if severity == "HIGH":
        return (
            f"Predicted load of {load_value:.1f} kW is strongly abnormal for "
            f"{temp_value:.1f}°C at hour {hour_value}."
        )
    if severity == "MEDIUM":
        return f"Unusual load pattern detected compared to normal {current_day} behavior."
    return "Slight deviation from expected load demand."


@app.route("/detect_anomaly", methods=["POST"])
def detect_anomaly():
    try:
        data = request.get_json() or {}
        if model is None or scaler is None:
            return jsonify({"is_anomaly": False, "error": "Model not loaded"}), 503

        feature_frame = build_feature_frame(data)
        features_scaled = scaler.transform(feature_frame)

        pred = model.predict(features_scaled)[0]
        raw_score = float(model.score_samples(features_scaled)[0])
        anomaly_score = float(-raw_score)

        decision_score = None
        if hasattr(model, "decision_function"):
            decision_score = float(model.decision_function(features_scaled)[0])

        is_anomaly = bool(pred == -1)
        anomaly_margin = max(0.0, -decision_score) if decision_score is not None else anomaly_score
        severity = classify_severity(anomaly_margin) if is_anomaly else "NORMAL"
        confidence = calculate_confidence(anomaly_score, decision_score)
        reason = build_reason(data, severity, is_anomaly)

        return jsonify(
            {
                "is_anomaly": is_anomaly,
                "anomaly_score": anomaly_score,
                "confidence": confidence,
                "severity": severity,
                "reason": reason,
                "model_name": selected_model_name,
            }
        )
    except Exception as error:
        return jsonify({"is_anomaly": False, "error": str(error)}), 500


@app.route("/health", methods=["GET"])
def health():
    return jsonify(
        {
            "status": "healthy",
            "model_loaded": model is not None,
            "scaler_loaded": scaler is not None,
            "selected_model": selected_model_name,
            "expected_features": feature_order,
            "metadata_loaded": bool(model_metadata),
        }
    )


if __name__ == "__main__":
    load_model()
    app.run(host="0.0.0.0", port=5002, debug=True)
