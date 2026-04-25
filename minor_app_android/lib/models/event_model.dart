/// Model representing a safety event detected by the on-device ML pipeline.
///
/// This model maps directly to the `eventos` table in the local database
/// and is the output of the OCR → NLP analysis pipeline.
class SafetyEvent {
  final String? id;
  final String type; // e.g., "predatory_behavior", "geofence_breach", "suspicious_content"
  final String severity; // "critical", "warning", "information"
  final String source; // "ocr", "vision", "nlp"
  final String rawText; // Original text from OCR
  final String nlpAnalysis; // NLP classification result
  final String? visionLabel; // Vision model classification if applicable
  final double confidenceScore; // 0.0 - 1.0
  final String platform; // e.g., "whatsapp", "instagram", "sms", "camera"
  final double? latitude;
  final double? longitude;
  final DateTime timestamp;
  final bool synced; // Whether it has been pushed to the police_app backend

  SafetyEvent({
    this.id,
    required this.type,
    required this.severity,
    required this.source,
    required this.rawText,
    required this.nlpAnalysis,
    this.visionLabel,
    required this.confidenceScore,
    required this.platform,
    this.latitude,
    this.longitude,
    DateTime? timestamp,
    this.synced = false,
  }) : timestamp = timestamp ?? DateTime.now();

  /// Convert to a Map for database insertion.
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'type': type,
      'severity': severity,
      'source': source,
      'raw_text': rawText,
      'nlp_analysis': nlpAnalysis,
      'vision_label': visionLabel,
      'confidence_score': confidenceScore,
      'platform': platform,
      'latitude': latitude,
      'longitude': longitude,
      'timestamp': timestamp.toIso8601String(),
      'synced': synced ? 1 : 0,
    };
  }

  /// Construct from a database row Map.
  factory SafetyEvent.fromMap(Map<String, dynamic> map) {
    return SafetyEvent(
      id: map['id']?.toString(),
      type: map['type'] ?? '',
      severity: map['severity'] ?? 'information',
      source: map['source'] ?? '',
      rawText: map['raw_text'] ?? '',
      nlpAnalysis: map['nlp_analysis'] ?? '',
      visionLabel: map['vision_label'],
      confidenceScore: (map['confidence_score'] as num?)?.toDouble() ?? 0.0,
      platform: map['platform'] ?? '',
      latitude: (map['latitude'] as num?)?.toDouble(),
      longitude: (map['longitude'] as num?)?.toDouble(),
      timestamp: map['timestamp'] != null
          ? DateTime.parse(map['timestamp'])
          : DateTime.now(),
      synced: map['synced'] == 1,
    );
  }
}
