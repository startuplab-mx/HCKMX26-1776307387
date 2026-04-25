"""
Motor de scoring táctico.
Puede usarse como paso del pipeline o importarse de forma independiente
para inferencia en producción.
"""

import logging
from pathlib import Path

log = logging.getLogger(__name__)


class TacticalScorer:
    """
    Evalúa detecciones de YOLOv8 y asigna un nivel de alerta
    según la combinación de clases por tier detectadas.
    """

    def __init__(self, cfg: dict):
        classes   = cfg["dataset"]["classes"]
        self.names = [c["name"] for c in classes]

        self.tier1 = {i for i, c in enumerate(classes) if c["tier"] == 1}
        self.tier2 = {i for i, c in enumerate(classes) if c["tier"] == 2}
        self.tier3 = {i for i, c in enumerate(classes) if c["tier"] == 3}

        self.conf_threshold = cfg["scoring"]["confidence_threshold"]

    def evaluate(self, results) -> dict:
        tier1_det: set[str] = set()
        tier2_det: set[str] = set()
        tier3_det: set[str] = set()

        for r in results:
            for box in r.boxes:
                cls_id = int(box.cls[0])
                conf   = float(box.conf[0])

                if conf < self.conf_threshold:
                    continue

                name = self.names[cls_id]
                if cls_id in self.tier1:
                    tier1_det.add(name)
                elif cls_id in self.tier2:
                    tier2_det.add(name)
                elif cls_id in self.tier3:
                    tier3_det.add(name)

        t1, t2 = len(tier1_det), len(tier2_det)

        if t1 >= 1 and t2 >= 2:
            level = "ALERTA RECLUTAMIENTO"
        elif t1 >= 1 and t2 >= 1:
            level = "FLAG ASPIRACIONALIDAD"
        elif t1 >= 1:
            level = "FLAG PASIVO"
        else:
            level = "SIN ALERTA"

        return {
            "level": level,
            "tier1": sorted(tier1_det),
            "tier2": sorted(tier2_det),
            "tier3": sorted(tier3_det),
        }

    def predict_image(self, model, image_path: str | Path) -> dict:
        """Conveniencia: infiere una imagen y devuelve el scoring."""
        results = model(str(image_path))
        return self.evaluate(results)


def run(cfg: dict, best_pt: Path) -> "TacticalScorer":
    """
    Instancia y valida el scorer. Devuelve el objeto listo para producción.
    """
    scorer = TacticalScorer(cfg)
    log.info("Scorer inicializado.")
    log.info("  Tier 1 (%d clases): %s",
             len(scorer.tier1), [scorer.names[i] for i in scorer.tier1])
    log.info("  Tier 2 (%d clases): %s",
             len(scorer.tier2), [scorer.names[i] for i in scorer.tier2])
    log.info("  Confianza mínima : %.2f", scorer.conf_threshold)
    return scorer
