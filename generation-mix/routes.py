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

# Training data ranges - for out of distribution check
TRAINING_RANGES = {
    "load_demand": {"min": 26799, "max": 47444},
    "reservoir_pct": {"min": 38.3, "max": 99.7},
    "wind_lag1": {"min": 500, "max": 3000},
    "solar_lag1": {"min": 300, "max": 2000},
    "mini_hydro_lag1": {"min": 800, "max": 4000},
}

# Expected prediction ranges
PREDICTION_RANGES = {
    "major_hydro": {"min": 0, "max": 35000},
    "total_coal": {"min": 0, "max": 25000},
    "total_thermal": {"min": 0, "max": 15000},
}

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


def _error(message, status_code=400):
    return jsonify({"error": message}), status_code


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
    if not os.path.exists(MODEL_PATH):
        raise FileNotFoundError("generation_model.pkl not found. Please train the model first.")
    if not os.path.exists(SCALER_PATH):
        raise FileNotFoundError("generation_scaler.pkl not found. Please train the model first.")

    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)
    if model is None or scaler is None:
        raise ValueError("Model artifacts failed to load.")
    return model, scaler


def check_out_of_distribution(row):
    warnings = []
    for feature, ranges in TRAINING_RANGES.items():
        if feature in row:
            val = row[feature]
            if val < ranges["min"] or val > ranges["max"]:
                warnings.append(
                    f"{feature} value {val} is outside training range ({ranges['min']} - {ranges['max']})"
                )
    return warnings


def check_prediction_sanity(major_hydro, total_coal, total_thermal, load_demand):
    warnings = []

    if major_hydro < 0:
        warnings.append("Major hydro prediction is negative!")
    if total_coal < 0:
        warnings.append("Coal prediction is negative!")
    if total_thermal < 0:
        warnings.append("Thermal prediction is negative!")

    for name, val, ranges in [
        ("major_hydro", major_hydro, PREDICTION_RANGES["major_hydro"]),
        ("total_coal", total_coal, PREDICTION_RANGES["total_coal"]),
        ("total_thermal", total_thermal, PREDICTION_RANGES["total_thermal"]),
    ]:
        if val > ranges["max"]:
            warnings.append(f"{name} prediction {val} MWh seems unrealistically high!")

    total_predicted = major_hydro + total_coal + total_thermal
    if load_demand > 0 and total_predicted > load_demand * 1.5:
        warnings.append("Total predicted generation is much higher than load demand!")
    if load_demand > 0 and total_predicted < load_demand * 0.1:
        warnings.append("Total predicted generation seems too low for the load demand!")

    return warnings


@generation_bp.route("/")
def home():
    return jsonify({
        "service": "generation-mix",
        "message": "POST to /predict with date, reservoir_pct, and load_demand.",
    })


@generation_bp.route("/health", methods=["GET"])
def health_check():
    try:
        model, scaler = load_model()
        return jsonify({
            "status": "healthy",
            "model_loaded": model is not None,
            "scaler_loaded": scaler is not None,
            "model_features": len(FEATURES),
            "message": "Generation Mix API is running!",
        })
    except Exception:
        return jsonify({
            "status": "unhealthy",
            "error": "Generation Mix model is not ready.",
        }), 500


@generation_bp.route("/predict", methods=["POST"])
def predict():
    try:
        data = request.get_json(silent=True)
        if not data:
            return _error("No data sent!", 400)

        if not data.get("date"):
            return _error("Date is required!", 400)
        if data.get("reservoir_pct") is None:
            return _error("Reservoir level is required!", 400)
        if data.get("load_demand") is None:
            return _error("Load demand is required!", 400)

        target_date = pd.to_datetime(data.get("date"), errors="coerce")
        if pd.isna(target_date):
            return _error("Invalid date format. Use YYYY-MM-DD.", 400)

        reservoir_pct = float(data.get("reservoir_pct"))
        load_demand = float(data.get("load_demand"))

        if not 0 <= reservoir_pct <= 100:
            return _error("Reservoir must be between 0 and 100!", 400)
        if load_demand < 20000:
            return _error("Load Demand must be at least 20,000 MWh!", 400)
        if load_demand > 60000:
            return _error("Load Demand cannot exceed 60,000 MWh!", 400)

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

        ood_warnings = check_out_of_distribution(row)

        features_frame = pd.DataFrame([row])[FEATURES]
        if features_frame.shape[1] != len(FEATURES):
            return _error(f"Feature mismatch. Expected {len(FEATURES)} features.", 500)
        if features_frame.isnull().any().any():
            return _error("Input contains missing values. Please check your inputs.", 400)

        model, scaler = load_model()
        scaler_features = getattr(scaler, "n_features_in_", None)
        if scaler_features is not None and scaler_features != len(FEATURES):
            return _error(f"Scaler expects {scaler_features} features but got {len(FEATURES)}.", 500)

        prediction = np.clip(model.predict(scaler.transform(features_frame))[0], 0, None)
        major_hydro = round(float(prediction[0]), 2)
        total_coal = round(float(prediction[1]), 2)
        total_thermal = round(float(prediction[2]), 2)

        sanity_warnings = check_prediction_sanity(major_hydro, total_coal, total_thermal, load_demand)

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
            "ml_warnings": ood_warnings + sanity_warnings,
            "out_of_distribution": len(ood_warnings) > 0,
        })
    except FileNotFoundError:
        return _error("Generation Mix model is not ready. Please train and deploy the model artifacts.", 500)
    except ValueError:
        return _error("Invalid numeric input. Please check your values.", 400)
    except Exception:
        return _error("Prediction failed due to an internal error.", 500)
