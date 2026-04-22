import unittest

import app as anomaly_app


class DummyScaler:
    def transform(self, frame):
        return frame


class DummyModel:
    def predict(self, features_scaled):
        return [1]

    def score_samples(self, features_scaled):
        return [-0.2]

    def decision_function(self, features_scaled):
        return [0.3]


class DetectAnomalyValidationTest(unittest.TestCase):
    def setUp(self):
        anomaly_app.app.config["TESTING"] = True
        self.client = anomaly_app.app.test_client()
        anomaly_app.model = DummyModel()
        anomaly_app.scaler = DummyScaler()
        anomaly_app.feature_order = anomaly_app.DEFAULT_FEATURE_ORDER
        anomaly_app.selected_model_name = "test_model"

    def test_detect_anomaly_rejects_non_numeric_feature_value(self):
        response = self.client.post("/detect_anomaly", json={"load": "not-a-number"})

        self.assertEqual(400, response.status_code)
        self.assertEqual({"error": "Feature 'load' must be numeric."}, response.get_json())


if __name__ == "__main__":
    unittest.main()
