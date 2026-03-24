import pandas as pd
import numpy as np
from sklearn.preprocessing import MinMaxScaler
from sklearn.metrics import mean_squared_error, mean_absolute_percentage_error
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout
import joblib
import math

# --------------------------------------------------
# 1. Load Dataset
# --------------------------------------------------
df = pd.read_csv("load_forecasting_dataset.csv")
df['Timestamp'] = pd.to_datetime(df['Timestamp'])

# --------------------------------------------------
# 2. Cyclical Time Encoding
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

features = [
    'Temperature (°C)',
    'Humidity (%)',
    'Public Event',
    'hour_sin', 'hour_cos',
    'day_sin', 'day_cos',
    'month_sin', 'month_cos'
]

target = 'Load Demand (kW)'

X = df[features]
y = df[target]

# --------------------------------------------------
# 3. Scaling
# --------------------------------------------------
scaler = MinMaxScaler()
X_scaled = scaler.fit_transform(X)
joblib.dump(scaler, "scaler.save")

# --------------------------------------------------
# 4. Create 24-hour Sequences
# --------------------------------------------------
def create_sequences(X, y, time_steps=24):
    Xs, ys = [], []
    for i in range(len(X) - time_steps):
        Xs.append(X[i:i + time_steps])
        ys.append(y.iloc[i + time_steps])
    return np.array(Xs), np.array(ys)

X_seq, y_seq = create_sequences(X_scaled, y)

# Train/Test split (time-series safe)
split = int(len(X_seq) * 0.8)
X_train, X_test = X_seq[:split], X_seq[split:]
y_train, y_test = y_seq[:split], y_seq[split:]

# --------------------------------------------------
# 5. LSTM Model
# --------------------------------------------------
model = Sequential([
    LSTM(64, return_sequences=True, input_shape=(24, 9)),
    Dropout(0.2),
    LSTM(32),
    Dropout(0.2),
    Dense(16, activation='relu'),
    Dense(1)
])

model.compile(optimizer='adam', loss='mean_squared_error')

model.fit(
    X_train,
    y_train,
    epochs=20,
    batch_size=32,
    validation_data=(X_test, y_test)
)

# --------------------------------------------------
# 6. Evaluation
# --------------------------------------------------
y_pred = model.predict(X_test)

rmse = math.sqrt(mean_squared_error(y_test, y_pred))
mape = mean_absolute_percentage_error(y_test, y_pred) * 100

print(f"RMSE: {rmse:.2f}")
print(f"MAPE: {mape:.2f}%")

# --------------------------------------------------
# 7. Save Model
# --------------------------------------------------
model.save("model.h5")
print("Model saved successfully")