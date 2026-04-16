from flask import Flask, request, jsonify
from flask_cors import CORS
import pandas as pd
import joblib
import os
import traceback

app = Flask(__name__)
CORS(app)  # Allow requests from Spring Boot

# Load the trained model
model_path = 'weather_predictor.pkl'
model = None

try:
    if os.path.exists(model_path):
        model = joblib.load(model_path)
        print("✅ Model loaded successfully from", model_path)
        print("📊 Model type:", type(model))
    else:
        print(f"❌ Model file not found at {model_path}")
        print("Current directory:", os.getcwd())
        print("Files in directory:", os.listdir())
except Exception as e:
    print(f"❌ Error loading model: {str(e)}")
    traceback.print_exc()

# Feature columns (must match training)
feature_cols = ['hour', 'day', 'month', 'dayofweek', 'year']

def predict_weather(timestamp_str):
    """
    Predict weather for given timestamp
    """
    if model is None:
        raise Exception("Model not loaded. Please ensure weather_predictor.pkl exists.")

    try:
        # Parse timestamp
        dt = pd.to_datetime(timestamp_str)

        # Create features
        features = pd.DataFrame([[
            dt.hour, dt.day, dt.month, dt.dayofweek, dt.year
        ]], columns=feature_cols)

        print(f"🔮 Predicting for: {timestamp_str}")
        print(f"📊 Features: hour={dt.hour}, day={dt.day}, month={dt.month}, dayofweek={dt.dayofweek}, year={dt.year}")

        # Predict
        pred = model.predict(features)[0]

        # Return predictions as dictionary
        result = {
            'temperature': float(pred[0]),
            'humidity': float(pred[1]),
            'wind_speed': float(pred[2]),
            'rainfall': float(pred[3]),
            'solar_irradiance': float(pred[4])
        }

        print(f"✅ Prediction result: {result}")
        return result

    except Exception as e:
        print(f"❌ Error in prediction: {str(e)}")
        traceback.print_exc()
        raise

@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json()
        print("📨 Received request:", data)

        date_time = data.get('date_time')

        if not date_time:
            return jsonify({'error': 'Date and time required'}), 400

        predictions = predict_weather(date_time)
        return jsonify(predictions)

    except Exception as e:
        print(f"❌ Error in predict endpoint: {str(e)}")
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'healthy',
        'model_loaded': model is not None,
        'model_path': model_path
    })

@app.route('/', methods=['GET'])
def home():
    return jsonify({
        'message': 'Weather Prediction API is running!',
        'endpoints': {
            'POST /predict': 'Predict weather for given date and time',
            'GET /health': 'Check API health status'
        }
    })

if __name__ == '__main__':
    print("=" * 50)
    print("🚀 Starting Weather Prediction API...")
    print(f"📁 Current directory: {os.getcwd()}")
    print(f"🔍 Looking for model at: {model_path}")
    print("=" * 50)
    app.run(debug=True, port=5000, host='0.0.0.0')