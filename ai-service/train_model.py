import pandas as pd
import numpy as np
import joblib
import math

from sklearn.preprocessing import MinMaxScaler
from sklearn.metrics import mean_squared_error, mean_absolute_percentage_error

from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout
from tensorflow.keras.callbacks import EarlyStopping

# --------------------------------------------------
# 1. Load Dataset
# --------------------------------------------------
df = pd.read_csv("load_forecasting_dataset.csv")
df.columns = df.columns.str.strip()

df['Timestamp'] = pd.to_datetime(df['Timestamp'])
df = df.sort_values('Timestamp')

# --------------------------------------------------
# 2. Fix / Encode Target
# --------------------------------------------------
target = 'Load Demand (kW)'

if target not in df.columns:
    raise Exception("Target column not found")

# --------------------------------------------------
# 3. Feature Engineering
# --------------------------------------------------
df['hour'] = df['Timestamp'].dt.hour
df['day'] = df['Timestamp'].dt.dayofweek
df['month'] = df['Timestamp'].dt.month

df['hour_sin'] = np.sin(2 * np.pi * df['hour'] / 24)
df['hour_cos'] = np.cos(2 * np.pi * df['hour'] / 24)

df['day_sin'] = np.sin(2 * np.pi * df['day'] / 7)
df['day_cos'] = np.cos(2 * np.pi * df['day'] / 7)

df['month_sin'] = np.sin(2 * np.pi * df['month'] / 12)
df['month_cos'] = np.cos(2 * np.pi * df['month'] / 12)

# Encode Public Event safely
if 'Public Event' in df.columns:
    df['Public Event'] = df['Public Event'].map({'Yes': 1, 'No': 0}).fillna(0)

# --------------------------------------------------
# 4. Feature & Target Selection
# --------------------------------------------------
features = [
    'Temperature (°C)',
    'Humidity (%)',
    'Public Event',
    'hour_sin', 'hour_cos',
    'day_sin', 'day_cos',
    'month_sin', 'month_cos'
]

X = df[features].values
y = df[[target]].values

# --------------------------------------------------
# 5. Scaling (IMPORTANT FIX)
# --------------------------------------------------
x_scaler = MinMaxScaler()
X_scaled = x_scaler.fit_transform(X)

y_scaler = MinMaxScaler()
y_scaled = y_scaler.fit_transform(y)

# Save scalers
joblib.dump(x_scaler, "x_scaler.save")
joblib.dump(y_scaler, "y_scaler.save")

# --------------------------------------------------
# 6. Create Sequences
# --------------------------------------------------
def create_sequences(X, y, time_steps=24):
    Xs, ys = [], []
    for i in range(len(X) - time_steps):
        Xs.append(X[i:i + time_steps])
        ys.append(y[i + time_steps])
    return np.array(Xs), np.array(ys)

X_seq, y_seq = create_sequences(X_scaled, y_scaled, 24)

# --------------------------------------------------
# 7. Train/Test Split (Time Series Safe)
# --------------------------------------------------
split = int(len(X_seq) * 0.8)

X_train, X_test = X_seq[:split], X_seq[split:]
y_train, y_test = y_seq[:split], y_seq[split:]

# --------------------------------------------------
# 8. Build LSTM Model
# --------------------------------------------------
model = Sequential([
    LSTM(64, return_sequences=True, input_shape=(24, X_train.shape[2])),
    Dropout(0.2),

    LSTM(32),
    Dropout(0.2),

    Dense(32, activation='relu'),
    Dense(1)
])

model.compile(optimizer='adam', loss='mse')

# --------------------------------------------------
# 9. Early Stopping (IMPORTANT)
# --------------------------------------------------
early_stop = EarlyStopping(
    monitor='val_loss',
    patience=7,
    restore_best_weights=True
)

# --------------------------------------------------
# 10. Train Model
# --------------------------------------------------
model.fit(
    X_train,
    y_train,
    epochs=50,
    batch_size=32,
    validation_data=(X_test, y_test),
    callbacks=[early_stop],
    verbose=1
)

# --------------------------------------------------
# 11. Predictions
# --------------------------------------------------
y_pred = model.predict(X_test)

# Inverse transform (IMPORTANT FIX)
y_test_inv = y_scaler.inverse_transform(y_test)
y_pred_inv = y_scaler.inverse_transform(y_pred)

# --------------------------------------------------
# 12. Evaluation
# --------------------------------------------------
rmse = math.sqrt(mean_squared_error(y_test_inv, y_pred_inv))
mape = mean_absolute_percentage_error(y_test_inv, y_pred_inv) * 100

print("\n📊 FINAL LSTM MODEL PERFORMANCE")
print("--------------------------------")
print(f"RMSE : {rmse:.2f}")
print(f"MAPE : {mape:.2f}%")

# --------------------------------------------------
# 13. Save Model
# --------------------------------------------------
model.save("lstm_model.h5")

print("\n✅ Model saved successfully!")