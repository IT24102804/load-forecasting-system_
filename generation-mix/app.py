from flask import Flask, jsonify

from routes import generation_bp

app = Flask(__name__)
app.register_blueprint(generation_bp)


@app.route("/app-health")
def health():
    return jsonify({"status": "ok", "service": "generation-mix"})


if __name__ == "__main__":
    app.run(debug=True, port=5003)
