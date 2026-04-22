import pandas as pd
import numpy as np
import joblib

from sklearn.preprocessing import MinMaxScaler
from sklearn.metrics import mean_squared_error, mean_absolute_percentage_error

from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout, Input
from tensorflow.keras.callbacks import EarlyStopping

# -----------------------------
# 1. Load Data
# -----------------------------
df = pd.read_csv("load_forecasting_dataset.csv")
df.columns = df.columns.str.strip()

df['Timestamp'] = pd.to_datetime(df['Timestamp'])
df = df.sort_values("Timestamp").reset_index(drop=True)

# -----------------------------
# 2. Encode categorical safely
# -----------------------------
if 'Public Event' in df.columns:
    df['Public Event'] = df['Public Event'].map({'Yes': 1, 'No': 0}).fillna(0)

# -----------------------------
# 3. Time features
# -----------------------------
df['hour'] = df['Timestamp'].dt.hour
df['day'] = df['Timestamp'].dt.dayofweek

df['hour_sin'] = np.sin(2*np.pi*df['hour']/24)
df['hour_cos'] = np.cos(2*np.pi*df['hour']/24)

df['day_sin'] = np.sin(2*np.pi*df['day']/7)
df['day_cos'] = np.cos(2*np.pi*df['day']/7)

# -----------------------------
# 4. Features & target
# -----------------------------
features = [
    'Temperature (°C)',
    'Humidity (%)',
    'Public Event',
    'hour_sin','hour_cos',
    'day_sin','day_cos'
]

target = 'Load Demand (kW)'

X = df[features].values
y = df[[target]].values

# -----------------------------
# 5. Scaling
# -----------------------------
x_scaler = MinMaxScaler()
y_scaler = MinMaxScaler()

X_scaled = x_scaler.fit_transform(X)
y_scaled = y_scaler.fit_transform(y)

joblib.dump(x_scaler, "x_scaler_24h.save")
joblib.dump(y_scaler, "y_scaler_24h.save")

# -----------------------------
# 6. Sequence creation (24 → 24)
# -----------------------------
def create_sequences(X, y, input_steps=24, output_steps=24):
    Xs, ys = [], []
    for i in range(len(X) - input_steps - output_steps):
        Xs.append(X[i:i+input_steps])
        ys.append(y[i+input_steps:i+input_steps+output_steps].flatten())
    return np.array(Xs), np.array(ys)

X_seq, y_seq = create_sequences(X_scaled, y_scaled)

# -----------------------------
# 7. Train/Test split
# -----------------------------
split = int(len(X_seq) * 0.8)

X_train, X_test = X_seq[:split], X_seq[split:]
y_train, y_test = y_seq[:split], y_seq[split:]

# -----------------------------
# 8. Model
# -----------------------------
model = Sequential([
    Input(shape=(24, len(features))),

    LSTM(128, return_sequences=True),
    Dropout(0.2),

    LSTM(64),
    Dropout(0.2),

    Dense(64, activation='relu'),
    Dense(24)
])

model.compile(optimizer='adam', loss='mse')

# -----------------------------
# 9. Training
# -----------------------------
model.fit(
    X_train, y_train,
    epochs=50,
    batch_size=32,
    validation_data=(X_test, y_test),
    callbacks=[EarlyStopping(patience=5, restore_best_weights=True)]
)

# -----------------------------
# 10. Prediction
# -----------------------------
y_pred = model.predict(X_test)

# inverse transform
y_test_inv = y_scaler.inverse_transform(y_test)
y_pred_inv = y_scaler.inverse_transform(y_pred)

# -----------------------------
# 11. Evaluation
# -----------------------------
rmse = np.sqrt(mean_squared_error(y_test_inv, y_pred_inv))
mape = mean_absolute_percentage_error(y_test_inv, y_pred_inv) * 100

print("\n📊 24-HOUR FORECAST MODEL PERFORMANCE")
print("--------------------------------------")
print("RMSE :", rmse)
print("MAPE :", mape, "%")

# -----------------------------
# 12. Save model
# -----------------------------
model.save("lstm_24h_model.h5")

print("\n✅ 24-hour forecasting model saved")