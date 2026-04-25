import '../models/event_model.dart';
import 'ocr_service.dart';
import 'vision_service.dart';
import 'nlp_service.dart';
import 'database_service.dart';

/// Orchestrator service that runs the full ML pipeline:
///
/// 1. **OCR Model** — Extracts text from an image.
/// 2. **Vision Model** — Classifies the image content independently.
/// 3. **NLP Model** — Analyzes the OCR-extracted text for safety threats.
/// 4. **Database Push** — Stores the resulting event in the `eventos` table.
///
/// The pipeline runs: OCR + Vision in parallel → NLP (depends on OCR) → DB insert.
class MlPipelineService {
  final OcrService _ocrService;
  final VisionService _visionService;
  final NlpService _nlpService;
  final DatabaseService _databaseService;

  bool _isInitialized = false;

  MlPipelineService({
    required OcrService ocrService,
    required VisionService visionService,
    required NlpService nlpService,
    required DatabaseService databaseService,
  })  : _ocrService = ocrService,
        _visionService = visionService,
        _nlpService = nlpService,
        _databaseService = databaseService;

  /// Initialize all three models and the database.
  Future<void> initialize() async {
    await Future.wait([
      _ocrService.initialize(),
      _visionService.initialize(),
      _nlpService.initialize(),
      _databaseService.initialize(),
    ]);
    _isInitialized = true;
  }

  bool get isReady => _isInitialized;

  /// Run the full analysis pipeline on an image.
  ///
  /// [imagePath] - Path to the image to analyze.
  /// [platform] - Source platform (e.g., "whatsapp", "camera", "instagram").
  /// [latitude] / [longitude] - Optional device location at capture time.
  ///
  /// Returns the created [SafetyEvent] with all analysis results.
  Future<PipelineResult> analyzeImage({
    required String imagePath,
    required String platform,
    double? latitude,
    double? longitude,
  }) async {
    if (!_isInitialized) {
      throw StateError('MlPipelineService not initialized.');
    }

    // Step 1: Run OCR and Vision models IN PARALLEL
    final results = await Future.wait([
      _ocrService.processImage(imagePath),
      _visionService.classifyImage(imagePath),
    ]);

    final ocrResult = results[0] as OcrResult;
    final visionResult = results[1] as VisionResult;

    // Step 2: Run NLP on the OCR-extracted text (sequential dependency)
    final nlpResult = await _nlpService.analyzeText(ocrResult.text);

    // Step 3: Determine overall severity and type
    final severity = _determineSeverity(nlpResult, visionResult);
    final type = _determineEventType(nlpResult, visionResult);

    // Step 4: Create the event
    final event = SafetyEvent(
      type: type,
      severity: severity,
      source: 'ml_pipeline',
      rawText: ocrResult.text,
      nlpAnalysis: nlpResult.summary,
      visionLabel: visionResult.primaryLabel,
      confidenceScore: _combineConfidence(
        ocrResult.confidence,
        nlpResult.confidence,
        visionResult.labels.isNotEmpty ? visionResult.labels.first.confidence : 0.0,
      ),
      platform: platform,
      latitude: latitude,
      longitude: longitude,
    );

    // Step 5: Push to local database (eventos table)
    final insertedId = await _databaseService.insertEvent(event);

    return PipelineResult(
      event: SafetyEvent(
        id: insertedId.toString(),
        type: event.type,
        severity: event.severity,
        source: event.source,
        rawText: event.rawText,
        nlpAnalysis: event.nlpAnalysis,
        visionLabel: event.visionLabel,
        confidenceScore: event.confidenceScore,
        platform: event.platform,
        latitude: event.latitude,
        longitude: event.longitude,
        timestamp: event.timestamp,
      ),
      ocrResult: ocrResult,
      visionResult: visionResult,
      nlpResult: nlpResult,
    );
  }

  /// Determine the highest severity from combined model results.
  String _determineSeverity(NlpResult nlp, VisionResult vision) {
    if (nlp.severity == 'critical' || vision.isFlagged) return 'critical';
    if (nlp.severity == 'warning') return 'warning';
    return 'information';
  }

  /// Determine the event type based on NLP category and vision labels.
  String _determineEventType(NlpResult nlp, VisionResult vision) {
    if (nlp.category != 'safe' && nlp.category != 'no_text') {
      return nlp.category; // e.g., "grooming", "bullying"
    }
    if (vision.isFlagged) {
      return 'flagged_image_${vision.primaryLabel}';
    }
    return 'normal_activity';
  }

  /// Combine confidence scores from all three models.
  double _combineConfidence(double ocr, double nlp, double vision) {
    // Weighted average: NLP matters most for text-based threats
    return (ocr * 0.2 + nlp * 0.5 + vision * 0.3).clamp(0.0, 1.0);
  }

  /// Dispose all services.
  void dispose() {
    _ocrService.dispose();
    _visionService.dispose();
    _nlpService.dispose();
    _databaseService.dispose();
    _isInitialized = false;
  }
}

/// Full result of the ML pipeline including all intermediate outputs.
class PipelineResult {
  final SafetyEvent event;
  final OcrResult ocrResult;
  final VisionResult visionResult;
  final NlpResult nlpResult;

  PipelineResult({
    required this.event,
    required this.ocrResult,
    required this.visionResult,
    required this.nlpResult,
  });
}
