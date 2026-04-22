from flask import Flask, request, jsonify
from flask_cors import CORS
import pandas as pd
import numpy as np
import joblib
import os
import traceback

app = Flask(__name__)
CORS(app)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "weather_predictor.pkl")
META_PATH = os.path.join(BASE_DIR, "weather_predictor_meta.pkl")
LOOKUP_PATH = os.path.join(BASE_DIR, "weather_avg_lookup.csv")

model = None
avg_lookup = None
FEATURE_COLS = None
TARGET_COLS = None


def get_season(month):
    if month in [12, 1, 2]:
        return 0
    if month in [3, 4]:
        return 1
    if month in [5, 6, 7, 8, 9]:
        return 2
    return 3


try:
    model = joblib.load(MODEL_PATH)
    if hasattr(model, "n_jobs"):
        model.n_jobs = 1
    print("Model loaded successfully.")
    print("Model type:", type(model))
except Exception as e:
    print("Error loading model:", str(e))
    traceback.print_exc()

try:
    meta = joblib.load(META_PATH)
    FEATURE_COLS = meta["feature_cols"]
    TARGET_COLS = meta["target_cols"]
    print("Meta loaded. Features:", len(FEATURE_COLS))
except Exception as e:
    print("Error loading meta:", str(e))
    traceback.print_exc()

try:
    avg_lookup = pd.read_csv(LOOKUP_PATH)
    print("Average lookup loaded. Shape:", avg_lookup.shape)
except Exception as e:
    print("Error loading avg_lookup:", str(e))
    traceback.print_exc()


def build_features(timestamp_str):
    dt = pd.to_datetime(timestamp_str)

    row = {
        "hour": dt.hour,
        "minute": dt.minute,
        "day": dt.day,
        "month": dt.month,
        "year": dt.year,
        "dayofweek": dt.dayofweek,
        "dayofyear": dt.dayofyear,
        "weekofyear": int(dt.isocalendar().week),
        "quarter": dt.quarter,
        "hour_sin": np.sin(2 * np.pi * dt.hour / 24),
        "hour_cos": np.cos(2 * np.pi * dt.hour / 24),
        "month_sin": np.sin(2 * np.pi * dt.month / 12),
        "month_cos": np.cos(2 * np.pi * dt.month / 12),
        "dayofyear_sin": np.sin(2 * np.pi * dt.dayofyear / 365),
        "dayofyear_cos": np.cos(2 * np.pi * dt.dayofyear / 365),
        "dayofweek_sin": np.sin(2 * np.pi * dt.dayofweek / 7),
        "dayofweek_cos": np.cos(2 * np.pi * dt.dayofweek / 7),
        "season_enc": get_season(dt.month),
        "is_daytime": int(6 <= dt.hour <= 18),
    }

    mask = (avg_lookup["hour"] == dt.hour) & (avg_lookup["month"] == dt.month)
    avgs = avg_lookup[mask]

    for col in TARGET_COLS:
        avg = float(avgs[col].values[0]) if len(avgs) > 0 else 0.0
        row[f"{col}_lag1"] = avg
        row[f"{col}_lag3"] = avg
        row[f"{col}_lag24"] = avg
        row[f"{col}_roll3"] = avg
        row[f"{col}_roll24"] = avg

    return pd.DataFrame([row])[FEATURE_COLS]


def predict_weather(timestamp_str):
    if model is None:
        raise Exception(f"Model not loaded. Ensure {MODEL_PATH} exists.")
    if avg_lookup is None:
        raise Exception(f"Avg lookup not loaded. Ensure {LOOKUP_PATH} exists.")
    if FEATURE_COLS is None:
        raise Exception(f"Meta not loaded. Ensure {META_PATH} exists.")

    features = build_features(timestamp_str)
    pred = model.predict(features)[0]

    return {
        "temperature": round(float(pred[0]), 2),
        "humidity": round(float(pred[1]), 2),
        "wind_speed": round(float(pred[2]), 2),
        "rainfall": max(0.0, round(float(pred[3]), 2)),
        "solar_irradiance": max(0.0, round(float(pred[4]), 2)),
    }


@app.route("/predict", methods=["POST"])
def predict():
    try:
        data = request.get_json()
        print("Received request:", data)

        date_time = data.get("date_time") if isinstance(data, dict) else None
        if not date_time:
            return jsonify({"error": "date_time is required"}), 400

        predictions = predict_weather(date_time)
        return jsonify(predictions)
    except Exception as e:
        print("Error in /predict:", str(e))
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/health", methods=["GET"])
def health():
    return jsonify(
        {
            "status": "healthy",
            "model_loaded": model is not None,
            "meta_loaded": FEATURE_COLS is not None,
            "lookup_loaded": avg_lookup is not None,
            "model_file": os.path.basename(MODEL_PATH),
        }
    )


@app.route("/", methods=["GET"])
def home():
    return jsonify(
        {
            "message": "Weather Prediction API is running.",
            "endpoints": {
                "POST /predict": "Predict weather for given date_time",
                "GET /health": "Check API health",
            },
        }
    )


if __name__ == "__main__":
    print("Starting Weather Prediction API...")
    print("Model file:", os.path.basename(MODEL_PATH))
    app.run(debug=True, port=5000, host="0.0.0.0")
