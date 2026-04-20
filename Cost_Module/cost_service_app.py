from flask import Flask, jsonify

from cost_service_routes import cost_bp

app = Flask(__name__)
app.register_blueprint(cost_bp)


if __name__ == "__main__":
    app.run(debug=True, port=5004)
