import os

import joblib
import numpy as np
import pandas as pd
from flask import Blueprint, jsonify, request

generation_bp = Blueprint("generation_bp", __name__)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "generation_model.pkl")
SCALER_PATH = os.path.join(BASE_DIR, "generation_scaler.pkl")

FEATURES = [
    "load_demand",
    "residual_load",
    "reservoir_pct",
    "wind_lag1",
    "solar_lag1",
    "mini_hydro_lag1",
    "dayofweek",
    "month",
    "quarter",
    "is_weekend",
    "season_encoded",
]

MONTHLY_WIND = {
    1: 1200, 2: 1100, 3: 1000, 4: 900,
    5: 1500, 6: 1800, 7: 1900, 8: 1850,
    9: 1700, 10: 1400, 11: 1300, 12: 1250,
}

MONTHLY_SOLAR = {
    1: 850, 2: 900, 3: 950, 4: 1000,
    5: 900, 6: 750, 7: 800, 8: 820,
    9: 780, 10: 820, 11: 800, 12: 780,
}

MONTHLY_MINI_HYDRO = {
    1: 2500, 2: 2200, 3: 1800, 4: 1600,
    5: 1400, 6: 1800, 7: 2200, 8: 2300,
    9: 2100, 10: 2000, 11: 2300, 12: 2600,
}


def get_season(month):
    if month in [5, 6, 7, 8, 9]:
        return 0
    if month in [10, 1, 2]:
        return 1
    return 2


def estimate_renewable(month, day, reservoir_pct):
    np.random.seed(month * 100 + day)

    base_wind = MONTHLY_WIND[month]
    base_solar = MONTHLY_SOLAR[month]
    base_mini_hydro = MONTHLY_MINI_HYDRO[month]

    wind_factor = 1 + np.random.uniform(-0.10, 0.10)
    solar_factor = 1 + np.random.uniform(-0.10, 0.10)
    mini_hydro_factor = 1 + np.random.uniform(-0.08, 0.08)

    if reservoir_pct > 80:
        mini_hydro_factor *= 1.15
    elif reservoir_pct < 40:
        mini_hydro_factor *= 0.85

    wind = round(base_wind * wind_factor, 2)
    solar = round(base_solar * solar_factor, 2)
    mini_hydro = round(base_mini_hydro * mini_hydro_factor, 2)

    return wind, solar, mini_hydro


def load_model():
    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)
    return model, scaler


@generation_bp.route("/")
def home():
    return jsonify({
        "service": "generation-mix",
        "message": "POST to /predict with date, reservoir_pct, and load_demand.",
    })


@generation_bp.route("/predict", methods=["POST"])
def predict():
    try:
        data = request.get_json(silent=True)
        if not data:
            return jsonify({"error": "No data sent!"}), 400

        target_date = pd.to_datetime(data.get("date"))
        reservoir_pct = float(data.get("reservoir_pct", 0))
        load_demand = float(data.get("load_demand", 37000))

        if not 0 <= reservoir_pct <= 100:
            return jsonify({"error": "Reservoir must be between 0 and 100"}), 400
        if load_demand < 20000:
            return jsonify({"error": "Load Demand must be at least 20000 MWh!"}), 400

        ts = pd.Timestamp(target_date)
        month = ts.month
        day = ts.day

        wind_lag1, solar_lag1, mini_hydro_lag1 = estimate_renewable(month, day, reservoir_pct)

        if "wind_lag1" in data:
            wind_lag1 = float(data.get("wind_lag1"))
        if "solar_lag1" in data:
            solar_lag1 = float(data.get("solar_lag1"))
        if "mini_hydro_lag1" in data:
            mini_hydro_lag1 = float(data.get("mini_hydro_lag1"))

        residual_load = max(load_demand - wind_lag1 - solar_lag1, 0.0)

        row = {
            "load_demand": load_demand,
            "residual_load": residual_load,
            "reservoir_pct": reservoir_pct,
            "wind_lag1": wind_lag1,
            "solar_lag1": solar_lag1,
            "mini_hydro_lag1": mini_hydro_lag1,
            "dayofweek": ts.dayofweek,
            "month": ts.month,
            "quarter": ts.quarter,
            "is_weekend": 1 if ts.dayofweek >= 5 else 0,
            "season_encoded": get_season(ts.month),
        }

        model, scaler = load_model()
        features_frame = pd.DataFrame([row])[FEATURES]
        prediction = np.clip(model.predict(scaler.transform(features_frame))[0], 0, None)
        major_hydro = round(float(prediction[0]), 2)
        total_coal = round(float(prediction[1]), 2)
        total_thermal = round(float(prediction[2]), 2)

        total = major_hydro + total_coal + total_thermal + wind_lag1 + solar_lag1 + mini_hydro_lag1
        total = max(total, 1)

        return jsonify({
            "date": str(target_date.date()),
            "inputs_used": {
                "wind_lag1": round(wind_lag1, 2),
                "solar_lag1": round(solar_lag1, 2),
                "mini_hydro_lag1": round(mini_hydro_lag1, 2),
            },
            "prediction": {
                "major_hydro": major_hydro,
                "total_coal": total_coal,
                "total_thermal": total_thermal,
                "wind": round(wind_lag1, 2),
                "solar": round(solar_lag1, 2),
                "mini_hydro": round(mini_hydro_lag1, 2),
            },
            "percentages": {
                "major_hydro": round(major_hydro / total * 100, 1),
                "total_coal": round(total_coal / total * 100, 1),
                "total_thermal": round(total_thermal / total * 100, 1),
                "wind": round(wind_lag1 / total * 100, 1),
                "solar": round(solar_lag1 / total * 100, 1),
                "mini_hydro": round(mini_hydro_lag1 / total * 100, 1),
            },
            "total_mwh": round(total, 2),
        })
    except Exception as exc:
        return jsonify({"error": str(exc)}), 500
