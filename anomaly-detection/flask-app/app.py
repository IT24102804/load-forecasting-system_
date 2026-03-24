from flask import Flask, request, jsonify
from flask_cors import CORS
import joblib
import numpy as np
import os

app = Flask(__name__)
CORS(app)

model = None
scaler = None
feature_order = None








 # Make sure request and jsonify are imported at the top

@app.route('/anomaly_feedback', methods=['POST'])
def anomaly_feedback():
    data = request.json
    # You can print it to the console for now just to prove it arrived
    print(f"Feedback received for prediction {data.get('prediction_id')}: User Agreed? {data.get('user_agreed')}")
    return jsonify({"status": "success", "message": "Feedback logged in Python!"}), 200















def load_model():
    global model, scaler, feature_order
    base_dir = os.path.dirname(os.path.abspath(__file__))

    model_path = os.path.join(base_dir, 'model.pkl')
    scaler_path = os.path.join(base_dir, 'scaler.pkl')

    if os.path.exists(model_path):
        model = joblib.load(model_path)
        print("✅ Model loaded.")
    else:
        print("❌ Model file not found.")

    if os.path.exists(scaler_path):
        scaler = joblib.load(scaler_path)
        print("✅ Scaler loaded.")
    else:
        print("❌ Scaler file not found.")

    # Define feature order (must match training)
    feature_order = ['load', 'temp', 'humidity', 'hour', 'day', 'month', 'event', 'season']
    print(f"📋 Expecting features: {feature_order}")

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
    return jsonify({
        'status': 'healthy',
        'model_loaded': model is not None,
        'scaler_loaded': scaler is not None,
        'expected_features': feature_order
    })

if __name__ == '__main__':
    load_model()
    app.run(host='0.0.0.0', port=5000, debug=True)