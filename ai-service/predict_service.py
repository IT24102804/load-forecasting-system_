from flask import Flask, request, jsonify
from flask_cors import CORS
import pandas as pd
import numpy as np
import joblib
from tensorflow.keras.models import load_model
from math import sin, cos, pi
import traceback
import os

app = Flask(__name__)
CORS(app)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

SINGLE_MODEL_PATH = os.path.join(BASE_DIR, "lstm_model.h5")
FORECAST_MODEL_PATH = os.path.join(BASE_DIR, "lstm_24h_model.h5")
X_SCALER_PATH = os.path.join(BASE_DIR, "x_scaler.save")
Y_SCALER_PATH = os.path.join(BASE_DIR, "y_scaler.save")
X_SCALER_24_PATH = os.path.join(BASE_DIR, "x_scaler_24h.save")
Y_SCALER_24_PATH = os.path.join(BASE_DIR, "y_scaler_24h.save")

single_model = None
forecast_model = None
x_scaler = None
y_scaler = None
x_scaler_24 = None
y_scaler_24 = None

try:
    single_model = load_model(SINGLE_MODEL_PATH, compile=False)
    forecast_model = load_model(FORECAST_MODEL_PATH, compile=False)
    x_scaler = joblib.load(X_SCALER_PATH)
    y_scaler = joblib.load(Y_SCALER_PATH)
    x_scaler_24 = joblib.load(X_SCALER_24_PATH)
    y_scaler_24 = joblib.load(Y_SCALER_24_PATH)
    print("All models and scalers loaded successfully")
except Exception:
    print("Error loading models/scalers")
    traceback.print_exc()

TIME_STEPS = 24

FEATURES = [
    "Temperature (Â°C)",
    "Humidity (%)",
    "Public Event",
    "hour_sin",
    "hour_cos",
    "day_sin",
    "day_cos",
    "month_sin",
    "month_cos",
]


def encode_time(ts):
    return {
        "hour_sin": sin(2 * pi * ts.hour / 24),
        "hour_cos": cos(2 * pi * ts.hour / 24),
        "day_sin": sin(2 * pi * ts.weekday() / 7),
        "day_cos": cos(2 * pi * ts.weekday() / 7),
        "month_sin": sin(2 * pi * ts.month / 12),
        "month_cos": cos(2 * pi * ts.month / 12),
    }


def validate_history(history):
    return isinstance(history, list) and len(history) == TIME_STEPS


def build_compat_history(payload):
    if not isinstance(payload, dict):
        raise ValueError("JSON object required")

    ts = pd.to_datetime(payload["timestamp"])
    temp = float(payload["temperature"])
    humidity = float(payload["humidity"])
    event = int(payload.get("public_event", 0))

    start = ts - pd.Timedelta(hours=TIME_STEPS - 1)
    history = []
    for offset in range(TIME_STEPS):
        history_ts = start + pd.Timedelta(hours=offset)
        history.append(
            {
                "timestamp": history_ts.isoformat(),
                "temperature": temp,
                "humidity": humidity,
                "public_event": event,
            }
        )
    return history


def extract_history(payload):
    if isinstance(payload, dict) and "history" in payload:
        return payload.get("history")
    return build_compat_history(payload)


def build_sequence(history, scaler):
    rows = []
    for item in history:
        ts = pd.to_datetime(item["timestamp"])
        row = {
            "Temperature (Â°C)": float(item["temperature"]),
            "Humidity (%)": float(item["humidity"]),
            "Public Event": int(item.get("public_event", 0)),
        }
        row.update(encode_time(ts))
        rows.append(row)

    df = pd.DataFrame(rows)[FEATURES]
    X_scaled = scaler.transform(df)
    return X_scaled.reshape(1, TIME_STEPS, len(FEATURES))


@app.route("/predict", methods=["POST"])
def predict():
    try:
        if single_model is None or x_scaler is None or y_scaler is None:
            return jsonify({"error": "Model not loaded"}), 500

        data = request.get_json()
        history = extract_history(data)
        if not validate_history(history):
            return jsonify({"error": "Exactly 24 records required"}), 400

        X_input = build_sequence(history, x_scaler)
        pred_scaled = single_model.predict(X_input, verbose=0)
        pred = y_scaler.inverse_transform(np.array(pred_scaled).reshape(-1, 1))[0][0]

        return jsonify({"load_demand": float(pred), "source": "python_model"})
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/forecast_24h", methods=["POST"])
def forecast_24h():
    try:
        if forecast_model is None or x_scaler_24 is None or y_scaler_24 is None:
            return jsonify({"error": "Forecast model not loaded"}), 500

        data = request.get_json()
        history = extract_history(data)
        if not validate_history(history):
            return jsonify({"error": "Exactly 24 records required"}), 400

        X_input = build_sequence(history, x_scaler_24)
        y_pred = forecast_model.predict(X_input, verbose=0)[0]
        y_pred = y_scaler_24.inverse_transform(y_pred.reshape(-1, 1)).flatten()

        return jsonify({"forecast": y_pred.tolist(), "source": "python_model"})
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/health", methods=["GET"])
def health():
    return jsonify(
        {
            "status": "healthy",
            "single_model_loaded": single_model is not None,
            "forecast_model_loaded": forecast_model is not None,
            "mode": "supports_history_and_single_payload",
        }
    )


@app.route("/", methods=["GET"])
def home():
    return jsonify({"status": "running", "endpoints": ["/predict", "/forecast_24h", "/health"]})


if __name__ == "__main__":
    print("Load Forecasting API Running...")
    print("Script directory:", BASE_DIR)
    app.run(host="0.0.0.0", port=5001, debug=True)
