from flask import Flask, request, jsonify
from flask_cors import CORS
import pandas as pd
import numpy as np
from tensorflow.keras.models import load_model
import joblib
from math import sin, cos, pi
import os
import traceback

app = Flask(__name__)
CORS(app)

# Load Models
try:
    lstm_model = load_model("lstm_model.h5")
    lstm_scaler = joblib.load("lstm_scaler.save")
    print("✅ LSTM Model and Scaler loaded successfully!")
except Exception as e:
    print("❌ Error loading models. Make sure .h5 and .save files are in this folder.")
    traceback.print_exc()

FEATURES = [
    "Temperature (°C)", "Humidity (%)", "Public Event",
    "hour_sin", "hour_cos", "day_sin", "day_cos", "month_sin", "month_cos"
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

def predict_lstm(temp, hum, event, ts):
    row = {
        "Temperature (°C)": temp,
        "Humidity (%)": hum,
        "Public Event": event
    }
    row.update(encode_time(ts))

    df = pd.DataFrame([row])[FEATURES]
    X_scaled = lstm_scaler.transform(df)

    # Matching your teammate's reshape logic
    X_scaled = X_scaled.reshape(1, 1, len(FEATURES))

    return float(lstm_model.predict(X_scaled, verbose=0)[0][0])

@app.route("/predict", methods=["POST"])
def predict():
    try:
        data = request.get_json()
        print("📨 Received Load Prediction request:", data)

        ts = pd.to_datetime(data["timestamp"])
        temp = float(data["temperature"])
        hum = float(data["humidity"])
        event = int(data.get("public_event", 0)) # Default to 0 if not provided

        pred = predict_lstm(temp, hum, event, ts)
        print(f"⚡ Predicted Load: {pred} kW")

        return jsonify({"load_demand": pred})

    except Exception as e:
        print(f"❌ Error: {str(e)}")
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    print("=" * 50)
    print("🚀 Starting Load Prediction API on Port 5001...")
    print("=" * 50)
    app.run(host="0.0.0.0", port=5001, debug=True)