import numpy as np
import tensorflow as tf
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent

MODEL_PATH = BASE_DIR / "recruitment_detector.tflite"
VOCAB_PATH = BASE_DIR / "vocab.txt"
LABELS_PATH = BASE_DIR / "labels.txt"

MAX_LEN = 60


def load_vocab(path):
    vocab = {}
    with open(path, "r", encoding="utf-8") as file:
        for index, token in enumerate(file.read().splitlines()):
            vocab[token] = index
    return vocab


def load_labels(path):
    with open(path, "r", encoding="utf-8") as file:
        return file.read().splitlines()


def text_to_sequence(text, vocab, max_len=50):
    text = text.lower()
    words = text.split()

    sequence = []
    for word in words:
        token_id = vocab.get(word, vocab.get("[UNK]", 1))
        sequence.append(token_id)

    if len(sequence) < max_len:
        sequence += [0] * (max_len - len(sequence))
    else:
        sequence = sequence[:max_len]

    return np.array([sequence], dtype=np.int64)


vocab = load_vocab(VOCAB_PATH)
labels = load_labels(LABELS_PATH)

interpreter = tf.lite.Interpreter(model_path=str(MODEL_PATH))
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print("Modelo cargado correctamente")
print("Input:", input_details)
print("Output:", output_details)


def predict(text):
    input_data = text_to_sequence(text, vocab, MAX_LEN)

    interpreter.set_tensor(input_details[0]["index"], input_data)
    interpreter.invoke()

    output = interpreter.get_tensor(output_details[0]["index"])
    score = float(output[0][0])

    label = "reclutamiento" if score >= 0.5 else "safe"

    print("\nTexto:", text)
    print("Predicción:", label)
    print("Score:", round(score, 4))


tests = [
    "#cha🍕",
    "hoy jugamos ranked en la noche",
    "ánimo plebada jálese a laborar 4 letras",
    "belicos",
    "🆖 reclutamiento",
    "🥷"
]

for text in tests:
    predict(text)