"""
Paso 4 — Exportación del modelo.
Convierte el checkpoint .pt a los formatos configurados
y empaqueta todo en un zip listo para desplegar.
"""

import zipfile
import logging
from pathlib import Path

log = logging.getLogger(__name__)


def run(cfg: dict, best_pt: Path) -> Path:
    from ultralytics import YOLO

    export_cfg  = cfg["export"]
    formats     = export_cfg.get("formats", ["tflite"])
    output_zip  = Path(export_cfg.get("output_zip", "modelo_tactical_v1.zip"))
    weights_dir = best_pt.parent

    model = YOLO(str(best_pt))

    exported_files: list[Path] = [best_pt]

    for fmt in formats:
        log.info("Exportando a formato: %s", fmt)
        model.export(format=fmt, imgsz=cfg["training"]["imgsz"])

        # YOLOv8 guarda el archivo exportado junto al .pt
        pattern = f"*.{fmt}" if fmt != "tflite" else "*.tflite"
        found = list(weights_dir.glob(pattern))
        if found:
            exported_files.extend(found)
            log.info("  Archivos generados: %s", [f.name for f in found])
        else:
            log.warning("  No se encontraron archivos .%s en %s", fmt, weights_dir)

    log.info("Empaquetando en: %s", output_zip)
    with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for f in exported_files:
            zf.write(f, f.name)
            log.info("  + %s", f.name)

    log.info("Exportación completa → %s", output_zip.resolve())
    return output_zip
