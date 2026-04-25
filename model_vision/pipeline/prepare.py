"""
Paso 1 — Preparación del dataset.
Copia imágenes y labels a la estructura que espera YOLOv8
y genera el dataset.yaml.
"""

import shutil
import yaml
import logging
from pathlib import Path

log = logging.getLogger(__name__)

VALID_IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp"}


def _resolve_dataset_dir(cfg: dict) -> Path:
    d = Path(cfg["dataset"]["dir"]).resolve()
    if not d.exists():
        raise FileNotFoundError(
            f"No se encontró el directorio del dataset: {d}\n"
            "Asegúrate de haber extraído el zip antes de correr el pipeline."
        )
    return d


def _copy_split(raw_images: Path, raw_labels: Path, dst_images: Path,
                dst_labels: Path, classes: list[str]) -> dict:
    """Copia imágenes y labels al destino; devuelve conteos por clase."""
    dst_images.mkdir(parents=True, exist_ok=True)
    dst_labels.mkdir(parents=True, exist_ok=True)

    counts = {}
    for clase in classes:
        img_src = raw_images / clase
        lbl_src = raw_labels / clase

        imgs_copied = 0
        lbls_copied = 0

        if img_src.exists():
            for img in img_src.glob("*"):
                if img.suffix.lower() in VALID_IMAGE_EXTS:
                    shutil.copy2(img, dst_images / img.name)
                    imgs_copied += 1
        else:
            log.warning("Carpeta de imágenes no encontrada: %s", img_src)

        if lbl_src.exists():
            for lbl in lbl_src.glob("*.txt"):
                shutil.copy2(lbl, dst_labels / lbl.name)
                lbls_copied += 1
        else:
            log.warning("Carpeta de labels no encontrada: %s", lbl_src)

        counts[clase] = {"images": imgs_copied, "labels": lbls_copied}

    return counts


def _verify_pairs(dst_images: Path, dst_labels: Path) -> dict:
    imgs = {f.stem for f in dst_images.glob("*") if f.suffix.lower() in VALID_IMAGE_EXTS}
    lbls = {f.stem for f in dst_labels.glob("*.txt")}
    return {
        "paired":    len(imgs & lbls),
        "no_label":  len(imgs - lbls),
        "no_image":  len(lbls - imgs),
        "orphan_examples": list(imgs - lbls)[:5],
    }


def _write_yaml(dataset_dir: Path, class_names: list[str]) -> Path:
    config = {
        "path":  str(dataset_dir),
        "train": "images/train",
        "val":   "images/train",   # mismo split — separar cuando haya más datos
        "nc":    len(class_names),
        "names": class_names,
    }
    yaml_path = dataset_dir / "dataset.yaml"
    with open(yaml_path, "w") as f:
        yaml.dump(config, f, sort_keys=False, allow_unicode=True)
    return yaml_path


def run(cfg: dict) -> dict:
    """Ejecuta la preparación del dataset y devuelve metadata útil."""
    dataset_dir = _resolve_dataset_dir(cfg)
    raw_images  = dataset_dir / "raw_images"
    raw_labels  = dataset_dir / "labels"
    dst_images  = dataset_dir / "images" / "train"
    dst_labels  = dataset_dir / "labels" / "train"   # YOLOv8 espeja images/ → labels/

    classes     = [c["name"] for c in cfg["dataset"]["classes"]]

    log.info("Copiando archivos al destino de entrenamiento...")
    counts = _copy_split(raw_images, raw_labels, dst_images, dst_labels, classes)

    for clase, c in counts.items():
        log.info("  %-12s  imágenes: %d   labels: %d", clase, c["images"], c["labels"])

    log.info("Verificando pares imagen-label...")
    pairs = _verify_pairs(dst_images, dst_labels)
    log.info("  Con label   : %d", pairs["paired"])
    log.info("  Sin label   : %d", pairs["no_label"])
    log.info("  Sin imagen  : %d", pairs["no_image"])
    if pairs["orphan_examples"]:
        log.warning("  Ejemplos sin label: %s", pairs["orphan_examples"])

    yaml_path = _write_yaml(dataset_dir, classes)
    log.info("YAML generado en: %s", yaml_path)

    return {
        "dataset_dir": dataset_dir,
        "yaml_path":   yaml_path,
        "counts":      counts,
        "pairs":       pairs,
    }
