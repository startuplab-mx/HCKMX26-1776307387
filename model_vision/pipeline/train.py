"""
Paso 2 — Entrenamiento YOLOv8.
"""

import logging
from pathlib import Path

log = logging.getLogger(__name__)


def run(cfg: dict, yaml_path: Path) -> Path:
    """
    Entrena el modelo y devuelve la ruta al mejor checkpoint.
    """
    from ultralytics import YOLO

    t   = cfg["training"]
    aug = t.get("augmentation", {})

    log.info("Cargando modelo base: %s", t["model"])
    model = YOLO(t["model"])

    log.info("Iniciando entrenamiento — epochs=%d  batch=%d  device=%s",
             t["epochs"], t["batch"], t["device"])

    model.train(
        data     = str(yaml_path),
        imgsz    = t["imgsz"],
        epochs   = t["epochs"],
        batch    = t["batch"],
        patience = t["patience"],
        device   = t["device"],
        project  = t["project"],
        name     = t["name"],
        exist_ok = True,
        save     = True,
        # augmentación
        hsv_h    = aug.get("hsv_h",   0.015),
        hsv_s    = aug.get("hsv_s",   0.7),
        degrees  = aug.get("degrees", 10.0),
        flipud   = aug.get("flipud",  0.0),
        fliplr   = aug.get("fliplr",  0.5),
        mosaic   = aug.get("mosaic",  1.0),
    )

    best_pt = Path(t["project"]) / t["name"] / "weights" / "best.pt"
    if not best_pt.exists():
        raise FileNotFoundError(f"No se generó el checkpoint esperado: {best_pt}")

    log.info("Entrenamiento completo. Checkpoint: %s", best_pt)
    return best_pt
