from flask import Flask, request, jsonify
import numpy as np
import pandas as pd
from tensorflow.keras.models import load_model
import joblib
from sqlalchemy import create_engine
from math import sin, cos, pi

app = Flask(__name__)

# -----------------------------
# Load model & scaler
# -----------------------------
model = load_model("model.h5")
scaler = joblib.load("scaler.save")

# -----------------------------
# SQL Server connection
# -----------------------------
DB_URL = (
    "mssql+pyodbc://sa:789@localhost:1433/load_forecastingqw"
    "?driver=ODBC+Driver+17+for+SQL+Server"
    "&encrypt=yes&trustServerCertificate=yes"
)

engine = create_engine(DB_URL)

# -----------------------------
# Feature list (MATCH TRAINING)
# -----------------------------
FEATURES = [
    "temperature",
    "humidity",
    "public_event",
    "hour_sin",
    "hour_cos",
    "day_sin",
    "day_cos",
    "month_sin",
    "month_cos",
]

# -----------------------------
# Time encoding
# -----------------------------
def encode_time(ts):
    hour = ts.hour
    day = ts.weekday()
    month = ts.month

    return {
        "hour_sin": sin(2 * pi * hour / 24),
        "hour_cos": cos(2 * pi * hour / 24),
        "day_sin": sin(2 * pi * day / 7),
        "day_cos": cos(2 * pi * day / 7),
        "month_sin": sin(2 * pi * month / 12),
        "month_cos": cos(2 * pi * month / 12),
    }

# -----------------------------
# Prediction endpoint
# -----------------------------
@app.route("/predict", methods=["POST"])
def predict():
    try:
        data = request.get_json()

        # 1️⃣ Current input
        ts = pd.to_datetime(data["timestamp"])
        temperature = float(data["temperature"])
        humidity = float(data["humidity"])
        public_event = int(data.get("public_event", data.get("publicEvent", 0)))

        time_features = encode_time(ts)

        current_row = {
            "temperature": temperature,
            "humidity": humidity,
            "public_event": public_event,
            **time_features,
        }

        # 2️⃣ Fetch last 23 rows from DB (REAL columns)
        query = """
            SELECT TOP 23
                temperature,
                humidity,
                public_event,
                timestamp
            FROM LoadRequests
            ORDER BY id DESC
        """

        df_hist = pd.read_sql(query, engine)

        # 3️⃣ Add time encoding to history
        if not df_hist.empty:
            df_hist["timestamp"] = pd.to_datetime(df_hist["timestamp"])
            time_df = df_hist["timestamp"].apply(encode_time).apply(pd.Series)
            df_hist = pd.concat([df_hist.drop(columns=["timestamp"]), time_df], axis=1)

        # 4️⃣ Combine history + current
        df_current = pd.DataFrame([current_row])
        full_df = pd.concat([df_hist, df_current], ignore_index=True)

        # Pad if less than 24
        if len(full_df) < 24:
            full_df = pd.concat(
                [full_df.iloc[[0]]] * (24 - len(full_df)) + [full_df],
                ignore_index=True
            )

        # Use last 24
        X = full_df[FEATURES].tail(24).values

        # 5️⃣ Scale & reshape
        X_scaled = scaler.transform(X)
        X_scaled = X_scaled.reshape(1, 24, len(FEATURES))

        # 6️⃣ Predict
        prediction = model.predict(X_scaled, verbose=0)
        load_value = float(prediction[0][0])

        return jsonify({"load_demand": load_value})

    except Exception as e:
        print("Prediction error:", e)
        return jsonify({"error": str(e)}), 500

# -----------------------------
# Run server
# -----------------------------
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=False)