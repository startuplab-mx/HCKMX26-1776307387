import '../models/event_model.dart';

/// Service for managing the local SQLite database.
///
/// Handles the `eventos` table where all ML-detected safety events
/// are stored before being synced to the police_app backend.
///
/// Integration point: Add `sqflite` to pubspec.yaml and replace
/// the mock implementation with real SQLite calls.
class DatabaseService {
  // TODO: Replace with real sqflite Database instance
  // late Database _db;
  bool _isInitialized = false;

  // In-memory store for mock purposes
  final List<SafetyEvent> _mockStore = [];
  int _autoIncrementId = 1;

  /// Initialize the database and create tables if needed.
  Future<void> initialize() async {
    // TODO: Replace with real SQLite initialization
    // _db = await openDatabase(
    //   join(await getDatabasesPath(), 'safeguard_events.db'),
    //   version: 1,
    //   onCreate: (db, version) async {
    //     await db.execute('''
    //       CREATE TABLE eventos (
    //         id INTEGER PRIMARY KEY AUTOINCREMENT,
    //         type TEXT NOT NULL,
    //         severity TEXT NOT NULL,
    //         source TEXT NOT NULL,
    //         raw_text TEXT NOT NULL,
    //         nlp_analysis TEXT NOT NULL,
    //         vision_label TEXT,
    //         confidence_score REAL NOT NULL,
    //         platform TEXT NOT NULL,
    //         latitude REAL,
    //         longitude REAL,
    //         timestamp TEXT NOT NULL,
    //         synced INTEGER DEFAULT 0
    //       )
    //     ''');
    //   },
    // );

    await Future.delayed(const Duration(milliseconds: 100));
    _isInitialized = true;
  }

  bool get isReady => _isInitialized;

  /// Insert a new safety event into the `eventos` table.
  ///
  /// Returns the inserted row ID.
  Future<int> insertEvent(SafetyEvent event) async {
    if (!_isInitialized) {
      throw StateError('DatabaseService not initialized.');
    }

    // TODO: Replace with real insert
    // return await _db.insert('eventos', event.toMap());

    final id = _autoIncrementId++;
    _mockStore.add(SafetyEvent(
      id: id.toString(),
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
      synced: event.synced,
    ));
    return id;
  }

  /// Get all events from the `eventos` table.
  Future<List<SafetyEvent>> getAllEvents() async {
    if (!_isInitialized) {
      throw StateError('DatabaseService not initialized.');
    }

    // TODO: Replace with real query
    // final maps = await _db.query('eventos', orderBy: 'timestamp DESC');
    // return maps.map((m) => SafetyEvent.fromMap(m)).toList();

    return List.from(_mockStore.reversed);
  }

  /// Get all unsynced events (to push to police_app backend).
  Future<List<SafetyEvent>> getUnsyncedEvents() async {
    if (!_isInitialized) {
      throw StateError('DatabaseService not initialized.');
    }

    // TODO: Replace with real query
    // final maps = await _db.query('eventos', where: 'synced = ?', whereArgs: [0]);
    // return maps.map((m) => SafetyEvent.fromMap(m)).toList();

    return _mockStore.where((e) => !e.synced).toList();
  }

  /// Mark an event as synced after successful push to backend.
  Future<void> markAsSynced(String eventId) async {
    if (!_isInitialized) {
      throw StateError('DatabaseService not initialized.');
    }

    // TODO: Replace with real update
    // await _db.update('eventos', {'synced': 1}, where: 'id = ?', whereArgs: [eventId]);

    final index = _mockStore.indexWhere((e) => e.id == eventId);
    if (index != -1) {
      final old = _mockStore[index];
      _mockStore[index] = SafetyEvent(
        id: old.id,
        type: old.type,
        severity: old.severity,
        source: old.source,
        rawText: old.rawText,
        nlpAnalysis: old.nlpAnalysis,
        visionLabel: old.visionLabel,
        confidenceScore: old.confidenceScore,
        platform: old.platform,
        latitude: old.latitude,
        longitude: old.longitude,
        timestamp: old.timestamp,
        synced: true,
      );
    }
  }

  /// Get count of events by severity.
  Future<Map<String, int>> getEventCountsBySeverity() async {
    if (!_isInitialized) {
      throw StateError('DatabaseService not initialized.');
    }

    // TODO: Replace with real aggregate query
    final counts = <String, int>{
      'critical': 0,
      'warning': 0,
      'information': 0,
    };
    for (final event in _mockStore) {
      counts[event.severity] = (counts[event.severity] ?? 0) + 1;
    }
    return counts;
  }

  /// Dispose database connection.
  Future<void> dispose() async {
    // TODO: await _db.close();
    _isInitialized = false;
  }
}
