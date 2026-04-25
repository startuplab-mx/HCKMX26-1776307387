/// Service that wraps the on-device OCR model.
///
/// This service is responsible for extracting text from images captured
/// by the device camera or from screenshots of chat apps.
///
/// Integration point: Replace the mock implementation with a real
/// ML model such as Google ML Kit Text Recognition or a custom TFLite model.
class OcrService {
  bool _isInitialized = false;

  /// Initialize the OCR model. Call once at app startup.
  Future<void> initialize() async {
    // TODO: Load the TFLite OCR model or initialize Google ML Kit
    // Example with ML Kit:
    //   _textRecognizer = TextRecognizer(script: TextRecognitionScript.latin);
    await Future.delayed(const Duration(milliseconds: 200)); // Simulate load
    _isInitialized = true;
  }

  bool get isReady => _isInitialized;

  /// Run OCR on an image and return the extracted text.
  ///
  /// [imagePath] - Absolute path to the image file on device.
  /// Returns the recognized text string.
  Future<OcrResult> processImage(String imagePath) async {
    if (!_isInitialized) {
      throw StateError('OcrService not initialized. Call initialize() first.');
    }

    // TODO: Replace with real OCR inference
    // Example with ML Kit:
    //   final inputImage = InputImage.fromFilePath(imagePath);
    //   final recognizedText = await _textRecognizer.processImage(inputImage);
    //   return OcrResult(
    //     text: recognizedText.text,
    //     confidence: _calculateAvgConfidence(recognizedText),
    //     blocks: recognizedText.blocks.map((b) => b.text).toList(),
    //   );

    await Future.delayed(const Duration(milliseconds: 300)); // Simulate inference
    return OcrResult(
      text: '[Mock OCR] Sample extracted text from image',
      confidence: 0.92,
      blocks: ['Block 1 text', 'Block 2 text'],
    );
  }

  /// Dispose the model resources.
  void dispose() {
    // TODO: _textRecognizer?.close();
    _isInitialized = false;
  }
}

/// Result from the OCR model containing extracted text and metadata.
class OcrResult {
  final String text;
  final double confidence;
  final List<String> blocks;

  OcrResult({
    required this.text,
    required this.confidence,
    required this.blocks,
  });
}
