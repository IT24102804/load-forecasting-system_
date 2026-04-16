from flask import Flask, request, jsonify
from flask_cors import CORS
import json
import joblib
import numpy as np
import os

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

@app.route('/detect_anomaly', methods=['POST'])
def detect_anomaly():
    try:
        data = request.get_json()
        if model is None or scaler is None:
            return jsonify({'is_anomaly': False, 'error': 'Model not loaded'}), 503

        # Build feature array in the exact order
        features = []
        for feat in feature_order:
            val = data.get(feat, 0)
            features.append(val)

        # Convert to numpy and scale
        features_array = np.array(features).reshape(1, -1)
        features_scaled = scaler.transform(features_array)

        # Predict
        pred = model.predict(features_scaled)[0]   # 1 = normal, -1 = anomaly
        score = model.score_samples(features_scaled)[0]

        return jsonify({
            'is_anomaly': bool(pred == -1),
            'anomaly_score': float(score),
            'confidence': float(abs(score)) if pred == -1 else float(1 - abs(score))
        })

    except Exception as e:
        return jsonify({'is_anomaly': False, 'error': str(e)}), 500

@app.route('/health', methods=['GET'])
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
