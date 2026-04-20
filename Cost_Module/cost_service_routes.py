import os

import joblib
import numpy as np
import pandas as pd
from flask import Blueprint, jsonify, request

cost_bp = Blueprint("cost_bp", __name__)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "energy_cost_model_final.pkl")
FEATURES_PATH = os.path.join(BASE_DIR, "features_list.pkl")

# Training data ranges - for out of distribution check
TRAINING_RANGES = {
    "load_demand": {"min": 26799, "max": 47444},
    "fo_price": {"min": 130.0, "max": 270.0},
    "coal_price": {"min": 36.0, "max": 55.0},
    "diesel_price": {"min": 225.0, "max": 370.0},
    "naphtha_price": {"min": 105.0, "max": 231.0},
}

REQUIRED_MODEL_FEATURES = {
    "major_hydro",
    "fo_price",
    "coal_price",
    "naphtha_price",
    "diesel_price",
    "load_demand",
    "month_sin",
    "month_cos",
    "thermal_ratio_ld",
    "hydro_ratio_ld",
    "coal_ratio_ld",
    "thermal_x_fo",
    "thermal_x_diesel",
    "thermal_x_naphtha",
    "coal_x_coalprice",
}

_model = None
_features = None


def _error(message, status_code=400):
    return jsonify({"error": message}), status_code


def _load_artifacts():
    global _model, _features
    if not os.path.exists(MODEL_PATH):
        raise FileNotFoundError("energy_cost_model_final.pkl not found. Please train and deploy the model.")
    if not os.path.exists(FEATURES_PATH):
        raise FileNotFoundError("features_list.pkl not found. Please train and deploy the model.")
    if _model is None:
        _model = joblib.load(MODEL_PATH)
    if _features is None:
        _features = joblib.load(FEATURES_PATH)
    if _model is None or _features is None:
        raise ValueError("Model artifacts failed to load.")


def _validate_feature_list(feature_names):
    missing = sorted(REQUIRED_MODEL_FEATURES.difference(feature_names))
    if missing:
        raise ValueError(
            "Cost model feature list is missing required features: " + ", ".join(missing)
        )


def _require_number(payload, key, *, label=None, min_value=None, max_value=None):
    if label is None:
        label = key
    if payload.get(key) is None:
        raise ValueError(f"{label} is required")
    try:
        val = float(payload.get(key))
    except Exception as exc:
        raise ValueError(f"{label} must be a valid number") from exc
    if not np.isfinite(val):
        raise ValueError(f"{label} must be a valid number")
    if min_value is not None and max_value is not None and (val < min_value or val > max_value):
        raise ValueError(f"{label} must be between {min_value} and {max_value}")
    if min_value is not None and max_value is None and val < min_value:
        raise ValueError(f"{label} must be at least {min_value}")
    if max_value is not None and min_value is None and val > max_value:
        raise ValueError(f"{label} must be at most {max_value}")
    return val


def check_out_of_distribution(payload):
    warnings = []
    for feature, ranges in TRAINING_RANGES.items():
        if payload.get(feature) is None:
            continue
        try:
            val = float(payload.get(feature))
        except Exception:
            continue
        if not np.isfinite(val):
            continue
        if val < ranges["min"] or val > ranges["max"]:
            label = {
                "fo_price": "FO Price",
                "coal_price": "Coal Price",
                "diesel_price": "Diesel Price",
                "naphtha_price": "Naphtha Price",
                "load_demand": "Load demand",
            }.get(feature, feature)
            warnings.append(
                f"{label} ({val}) is outside training range ({ranges['min']}–{ranges['max']})."
            )
    return warnings


def _normalize_input(feature_names, payload):
    base = dict(payload)
    if "date" in base and base.get("date") is not None:
        ts = pd.to_datetime(base.get("date"), errors="coerce")
    else:
        ts = None

    def f(key, default=0.0):
        val = base.get(key)
        if val is None:
            return float(default)
        return float(val)

    load_demand = f("load_demand", 0.0)
    total_thermal = f("total_thermal", 0.0)
    major_hydro = f("major_hydro", 0.0)
    total_coal = f("total_coal", 0.0)
    fo_price = f("fo_price", 0.0)
    diesel_price = f("diesel_price", 0.0)
    naphtha_price = f("naphtha_price", 0.0)
    coal_price = f("coal_price", 0.0)

    if load_demand != 0:
        base.setdefault("thermal_ratio_ld", total_thermal / load_demand)
        base.setdefault("hydro_ratio_ld", major_hydro / load_demand)
        base.setdefault("coal_ratio_ld", total_coal / load_demand)

    base.setdefault("thermal_x_fo", total_thermal * fo_price)
    base.setdefault("thermal_x_diesel", total_thermal * diesel_price)
    base.setdefault("thermal_x_naphtha", total_thermal * naphtha_price)
    base.setdefault("coal_x_coalprice", total_coal * coal_price)

    if ts is not None and not pd.isna(ts):
        base.setdefault("year", int(ts.year))
        base.setdefault("is_weekend", 1 if ts.dayofweek >= 5 else 0)
        month = ts.month
        base.setdefault("month_sin", float(np.sin(2 * np.pi * month / 12)))
        base.setdefault("month_cos", float(np.cos(2 * np.pi * month / 12)))

    row = {}
    for name in feature_names:
        val = base.get(name)
        if val is None:
            row[name] = 0.0
        else:
            row[name] = float(val)
    return row


@cost_bp.route("/")
def home():
    return jsonify({
        "service": "cost-prediction",
        "message": "POST to /predict with feature values (including fuel prices) to get unit_cost.",
    })


@cost_bp.route("/health", methods=["GET"])
def health_check():
    try:
        _load_artifacts()
        return jsonify({
            "status": "healthy",
            "model_loaded": _model is not None,
            "features_loaded": _features is not None,
            "feature_count": len(list(_features)) if _features is not None else 0,
            "message": "Cost Prediction API is running!",
        })
    except Exception:
        return jsonify({
            "status": "unhealthy",
            "error": "Cost model is not ready.",
        }), 500


@cost_bp.route("/predict", methods=["POST"])
def predict():
    try:
        _load_artifacts()
        payload = request.get_json(silent=True)
        if not payload:
            return _error("No data sent!", 400)

        if not payload.get("date"):
            return _error("date is required!", 400)

        try:
            ts = pd.to_datetime(payload.get("date"), errors="coerce")
            if pd.isna(ts):
                return _error("Invalid date format. Use YYYY-MM-DD.", 400)
        except Exception:
            return _error("Invalid date format. Use YYYY-MM-DD.", 400)

        feature_names = list(_features)
        try:
            _validate_feature_list(set(feature_names))
        except ValueError as exc:
            return _error(str(exc), 500)

        load_demand = _require_number(payload, "load_demand", label="Load demand", min_value=1)
        _require_number(payload, "fo_price", label="FO Price", min_value=0.0000001, max_value=2000)
        _require_number(payload, "coal_price", label="Coal Price", min_value=0.0000001, max_value=2000)
        _require_number(payload, "diesel_price", label="Diesel Price", min_value=0.0000001, max_value=2000)
        _require_number(payload, "naphtha_price", label="Naphtha Price", min_value=0.0000001, max_value=2000)

        major_hydro = _require_number(payload, "major_hydro", min_value=0.0000001)
        total_coal = _require_number(payload, "total_coal", min_value=0.0000001)
        total_thermal = _require_number(payload, "total_thermal", min_value=0.0000001)

        ood_warnings = check_out_of_distribution(payload)
        row = _normalize_input(feature_names, payload)
        X = pd.DataFrame([row], columns=feature_names)

        if X.shape[1] != len(feature_names):
            return _error("Feature mismatch in cost model payload.", 500)

        if X.isnull().any().any():
            return _error("Input contains missing values. Please check your inputs.", 400)

        pred = float(_model.predict(X)[0])

        return jsonify({
            "unit_cost": round(pred, 4),
            "ml_warnings": ood_warnings,
            "out_of_distribution": len(ood_warnings) > 0,
        })

    except FileNotFoundError:
        return _error("Cost model is not ready. Please train and deploy the model artifacts.", 500)
    except ValueError as exc:
        msg = str(exc)
        if msg.endswith(" is required"):
            msg = msg.replace(" is required", " is required!")
        return _error(msg, 400)
    except Exception:
        return _error("Cost prediction failed due to an internal error.", 500)
