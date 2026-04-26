"""
vision_tactical — pipeline principal

Uso:
  # Pipeline completo
  python main.py

  # Saltar preparación (ya tienes images/train y labels/train listos)
  python main.py --skip-prepare

  # Solo exportar un checkpoint ya entrenado
  python main.py --only-export runs/vision_tactical_v1/weights/best.pt

  # Inferencia sobre una imagen
  python main.py --infer ruta/imagen.jpg

  # Config personalizada
  python main.py --config otra_config.yaml
"""

import argparse
import logging
import sys
import time
from pathlib import Path

import yaml


# ── Logging ─────────────────────────────────────────────────────────────────

def setup_logging(level: str = "INFO") -> None:
    fmt = "%(asctime)s  %(levelname)-8s  %(name)s — %(message)s"
    logging.basicConfig(level=getattr(logging, level.upper(), logging.INFO),
                        format=fmt, datefmt="%H:%M:%S")


# ── Config ───────────────────────────────────────────────────────────────────

def load_config(path: str = "config/config.yaml") -> dict:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"Archivo de configuración no encontrado: {p}")
    with open(p) as f:
        return yaml.safe_load(f)


# ── Pasos ────────────────────────────────────────────────────────────────────

def step(name: str):
    """Decorador de logging para cada etapa del pipeline."""
    def decorator(fn):
        def wrapper(*args, **kwargs):
            log = logging.getLogger("pipeline")
            sep = "─" * 60
            log.info(sep)
            log.info("▶  %s", name)
            log.info(sep)
            t0 = time.time()
            result = fn(*args, **kwargs)
            log.info("✓  %s completado en %.1fs", name, time.time() - t0)
            return result
        return wrapper
    return decorator


@step("Preparación del dataset")
def run_prepare(cfg):
    from pipeline import prepare
    return prepare.run(cfg)


@step("Entrenamiento")
def run_train(cfg, yaml_path):
    from pipeline import train
    return train.run(cfg, yaml_path)


@step("Scoring / validación")
def run_score(cfg, best_pt):
    from pipeline import score
    return score.run(cfg, best_pt)


@step("Exportación")
def run_export(cfg, best_pt):
    from pipeline import export
    return export.run(cfg, best_pt)


# ── Inferencia standalone ────────────────────────────────────────────────────

def run_infer(cfg: dict, image_path: str) -> None:
    from ultralytics import YOLO
    from pipeline.score import TacticalScorer

    t   = cfg["training"]
    pt  = Path(t["project"]) / t["name"] / "weights" / "best.pt"

    if not pt.exists():
        sys.exit(f"No se encontró el modelo entrenado en {pt}. "
                 "Corre primero el pipeline completo.")

    log = logging.getLogger("infer")
    log.info("Modelo : %s", pt)
    log.info("Imagen : %s", image_path)

    model   = YOLO(str(pt))
    scorer  = TacticalScorer(cfg)
    results = model(image_path)
    alert   = scorer.evaluate(results)

    print("\n" + "═" * 40)
    print(f"  Nivel  : {alert['level']}")
    print(f"  Tier 1 : {alert['tier1']}")
    print(f"  Tier 2 : {alert['tier2']}")
    print("═" * 40 + "\n")


# ── CLI ───────────────────────────────────────────────────────────────────────

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Pipeline de entrenamiento vision_tactical",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    p.add_argument("--config",        default="config/config.yaml",
                   help="Ruta al archivo de configuración (default: config/config.yaml)")
    p.add_argument("--skip-prepare",  action="store_true",
                   help="Omite la preparación del dataset")
    p.add_argument("--skip-train",    action="store_true",
                   help="Omite el entrenamiento (requiere checkpoint existente)")
    p.add_argument("--skip-export",   action="store_true",
                   help="Omite la exportación final")
    p.add_argument("--only-export",   metavar="PT",
                   help="Solo exporta el checkpoint indicado y sale")
    p.add_argument("--infer",         metavar="IMAGE",
                   help="Corre inferencia sobre una imagen y sale")
    p.add_argument("--log-level",     default="INFO",
                   choices=["DEBUG", "INFO", "WARNING", "ERROR"],
                   help="Nivel de logging (default: INFO)")
    return p


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    args = build_parser().parse_args()
    setup_logging(args.log_level)
    log  = logging.getLogger("main")

    cfg  = load_config(args.config)

    # ── Modo inferencia ──────────────────────────────────────────────────────
    if args.infer:
        run_infer(cfg, args.infer)
        return

    # ── Modo solo-exportar ───────────────────────────────────────────────────
    if args.only_export:
        best_pt = Path(args.only_export)
        if not best_pt.exists():
            sys.exit(f"Checkpoint no encontrado: {best_pt}")
        run_export(cfg, best_pt)
        return

    # ── Pipeline completo ────────────────────────────────────────────────────
    yaml_path = None
    best_pt   = None

    t = cfg["training"]
    default_pt = Path(t["project"]) / t["name"] / "weights" / "best.pt"

    if not args.skip_prepare:
        meta      = run_prepare(cfg)
        yaml_path = meta["yaml_path"]
    else:
        yaml_path = Path(cfg["dataset"]["dir"]) / "dataset.yaml"
        log.info("Preparación omitida — usando YAML existente: %s", yaml_path)

    if not args.skip_train:
        best_pt = run_train(cfg, yaml_path)
    else:
        best_pt = default_pt
        if not best_pt.exists():
            sys.exit(f"--skip-train activo pero no hay checkpoint en {best_pt}")
        log.info("Entrenamiento omitido — checkpoint: %s", best_pt)

    run_score(cfg, best_pt)

    if not args.skip_export:
        output_zip = run_export(cfg, best_pt)
        log.info("Modelo listo para despliegue: %s", output_zip)
    else:
        log.info("Exportación omitida.")

    log.info("Pipeline finalizado.")


if __name__ == "__main__":
    main()
