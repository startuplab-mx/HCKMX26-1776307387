import json
from pathlib import Path

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline


BASE_DIR = Path(__file__).resolve().parents[1]
DATASET_PATH = BASE_DIR / "dataset" / "nlp_data" / "recruitment_dataset.json"
MODEL_PATH = Path(__file__).resolve().parent / "recruitment_detector.pkl"

with open(DATASET_PATH, "r", encoding="utf-8") as file:
    data = json.load(file)

texts = [item["text"] for item in data]
labels = [item["label"] for item in data]

model = Pipeline([
    ("tfidf", TfidfVectorizer(lowercase=True, ngram_range=(1, 2))),
    ("classifier", LogisticRegression(max_iter=1000))
])

model.fit(texts, labels)

tests = [
    "manda mensaje para informes hay buena paga",
    "hoy jugamos ranked en la noche",
    "ánimo plebada jálese a laborar",
    "ya hiciste la tarea"
]

print("\nPruebas:")
for text in tests:
    prediction = model.predict([text])[0]
    confidence = max(model.predict_proba([text])[0])
    print(f"{text} -> {prediction} ({confidence:.4f})")

joblib.dump(model, MODEL_PATH)

print(f"\nModelo guardado en: {MODEL_PATH}")