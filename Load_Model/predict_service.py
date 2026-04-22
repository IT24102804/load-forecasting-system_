import os
import runpy

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(BASE_DIR)
TARGET_SCRIPT = os.path.join(PROJECT_ROOT, "ai-service", "predict_service.py")

if __name__ == "__main__":
    runpy.run_path(TARGET_SCRIPT, run_name="__main__")
