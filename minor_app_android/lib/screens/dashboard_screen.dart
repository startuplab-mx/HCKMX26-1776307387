import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../widgets/dashboard_cards.dart';
import '../services/ml_pipeline_service.dart';
import '../services/ocr_service.dart';
import '../services/vision_service.dart';
import '../services/nlp_service.dart';
import '../services/database_service.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  static const MethodChannel _ocrChannel = MethodChannel('com.minorapp/ocr');
  static const MethodChannel _monitorChannel = MethodChannel('com.minorapp/monitor');

  late final MlPipelineService _pipeline;
  bool _pipelineReady = false;
  String _trustCode = '482-911';
  String _trustCodeValidity = '5:00 mins';
  final bool _isSecure = true;

  @override
  void initState() {
    super.initState();
    _setupNativeListeners();
    _initializePipeline();
  }

  void _setupNativeListeners() {
    _ocrChannel.setMethodCallHandler((call) async {
      debugPrint('[Native OCR] ${call.method}: ${call.arguments}');
    });

    _monitorChannel.setMethodCallHandler((call) async {
      debugPrint('[Native Monitor] ${call.method}: ${call.arguments}');
    });
  }

  Future<void> _initializePipeline() async {
    _pipeline = MlPipelineService(
      ocrService: OcrService(),
      visionService: VisionService(),
      nlpService: NlpService(),
      databaseService: DatabaseService(),
    );

    await _pipeline.initialize();

    if (mounted) {
      setState(() {
        _pipelineReady = true;
      });
    }
  }

  void _generateNewTrustCode() {
    final random = Random();
    final part1 = (100 + random.nextInt(900)).toString();
    final part2 = (100 + random.nextInt(900)).toString();
    setState(() {
      _trustCode = '$part1-$part2';
      _trustCodeValidity = '5:00 mins';
    });
  }

  /// Example: trigger the ML pipeline on an image.
  /// Call this from a button or from a background service.
  Future<void> _runAnalysisOnImage(String imagePath) async {
    if (!_pipelineReady) return;

    final result = await _pipeline.analyzeImage(
      imagePath: imagePath,
      platform: 'camera',
      latitude: 19.4326,
      longitude: -99.1332,
    );

    // The event is now stored in the local DB.
    // If severity is critical or warning, we could show a notification.
    if (result.event.severity == 'critical') {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('⚠️ Critical safety event detected!'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  void dispose() {
    _ocrChannel.setMethodCallHandler(null);
    _monitorChannel.setMethodCallHandler(null);
    _pipeline.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            // Top App Bar
            _buildTopBar(),

            // Scrollable content
            Expanded(
              child: SingleChildScrollView(
                physics: const BouncingScrollPhysics(),
                padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
                child: Column(
                  children: [
                    // Connection Status Card
                    StatusCard(
                      icon: Icons.check_circle,
                      iconColor: const Color(0xFF22C55E),
                      title: _isSecure ? 'Secure' : 'Not Secure',
                      subtitle: _isSecure
                          ? 'Your connection is active and protected.'
                          : 'Connection issue detected.',
                      backgroundColor: _isSecure
                          ? const Color(0xFFECFDF5)
                          : const Color(0xFFFEF2F2),
                    ),

                    const SizedBox(height: 14),

                    // Current Location Card
                    _buildLocationCard(),

                    const SizedBox(height: 14),

                    // Trust Code Card
                    TrustCodeCard(
                      code: _trustCode,
                      validFor: _trustCodeValidity,
                      onGenerate: _generateNewTrustCode,
                    ),

                    const SizedBox(height: 14),

                    // Security Analysis Card
                    SecurityAnalysisCard(
                      hasUpdate: true,
                      onTap: () {
                        // Navigate to security analysis details
                      },
                    ),

                    const SizedBox(height: 14),

                    // Pipeline status indicator
                    _buildPipelineStatus(),

                    const SizedBox(height: 80), // Bottom padding for FAB & nav
                  ],
                ),
              ),
            ),
          ],
        ),
      ),

      // SOS Floating Action Button
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          // SOS action
        },
        backgroundColor: const Color(0xFFEF4444),
        elevation: 6,
        child: const Text(
          'SOS',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w900,
            color: Colors.white,
          ),
        ),
      ),

      // Bottom Navigation Bar
      bottomNavigationBar: _buildBottomNav(),
    );
  }

  Widget _buildTopBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: const Color(0xFF1A1D29),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(
              Icons.shield,
              color: Colors.white,
              size: 18,
            ),
          ),
          const SizedBox(width: 12),
          const Text(
            'SafeGuard',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w800,
              color: Color(0xFF1A1D29),
            ),
          ),
          const Spacer(),
          Container(
            width: 34,
            height: 34,
            decoration: BoxDecoration(
              color: const Color(0xFF3B82F6).withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.person,
              color: Color(0xFF3B82F6),
              size: 20,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLocationCard() {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.grey.shade200),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
            child: Row(
              children: [
                const Icon(
                  Icons.location_on,
                  color: Color(0xFF1A1D29),
                  size: 18,
                ),
                const SizedBox(width: 6),
                const Text(
                  'Current Location',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF1A1D29),
                  ),
                ),
                const Spacer(),
                Text(
                  'Updated Just Now',
                  style: TextStyle(
                    fontSize: 11,
                    color: Colors.grey.shade500,
                  ),
                ),
              ],
            ),
          ),

          Container(
            width: double.infinity,
            height: 160,
            color: const Color(0xFFE8F0E8),
            child: Stack(
              alignment: Alignment.center,
              children: [
                Positioned.fill(
                  child: CustomPaint(
                    painter: _LocalMapPainter(),
                  ),
                ),
                Container(
                  width: 100,
                  height: 100,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: const Color(0xFF3B82F6).withValues(alpha: 0.5),
                      width: 2,
                      strokeAlign: BorderSide.strokeAlignInside,
                    ),
                    color: const Color(0xFF3B82F6).withValues(alpha: 0.08),
                  ),
                ),
                // Pin
                const Icon(
                  Icons.location_on,
                  color: Color(0xFF3B82F6),
                  size: 28,
                ),
              ],
            ),
          ),

          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                const Expanded(
                  child: Text(
                    '123 Safe Street, Cityville',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                      color: Color(0xFF1A1D29),
                    ),
                  ),
                ),
                ElevatedButton(
                  onPressed: () {},
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF1A1D29),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 10,
                    ),
                    elevation: 0,
                  ),
                  child: const Text(
                    'Directions',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPipelineStatus() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        children: [
          Icon(
            _pipelineReady ? Icons.check_circle : Icons.hourglass_top,
            color: _pipelineReady
                ? const Color(0xFF22C55E)
                : const Color(0xFFF59E0B),
            size: 20,
          ),
          const SizedBox(width: 10),
          Text(
            _pipelineReady
                ? 'ML Models Ready (OCR · Vision · NLP)'
                : 'Loading ML Models...',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: Colors.grey.shade700,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomNav() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 10,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: BottomNavigationBar(
        currentIndex: 0,
        type: BottomNavigationBarType.fixed,
        backgroundColor: Colors.white,
        selectedItemColor: const Color(0xFF3B82F6),
        unselectedItemColor: Colors.grey.shade500,
        selectedFontSize: 11,
        unselectedFontSize: 11,
        elevation: 0,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.grid_view_rounded),
            label: 'Status',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.location_on_outlined),
            label: 'Maps',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.description_outlined),
            label: 'Reports',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.shield_outlined),
            label: 'Safety',
          ),
        ],
      ),
    );
  }
}

class _LocalMapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final background = Paint()..color = const Color(0xFFE8F0E8);
    canvas.drawRect(Offset.zero & size, background);

    final roadPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.9)
      ..strokeWidth = 10
      ..strokeCap = StrokeCap.round;
    final minorRoadPaint = Paint()
      ..color = const Color(0xFFC9D8CA)
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round;
    final parkPaint = Paint()..color = const Color(0xFFCFE6D0);

    canvas.drawOval(
      Rect.fromCenter(
        center: Offset(size.width * 0.2, size.height * 0.25),
        width: size.width * 0.45,
        height: size.height * 0.4,
      ),
      parkPaint,
    );

    for (var i = -1; i < 5; i++) {
      final y = size.height * (0.18 + i * 0.18);
      canvas.drawLine(
        Offset(-20, y),
        Offset(size.width + 20, y + size.height * 0.08),
        minorRoadPaint,
      );
    }

    for (var i = 0; i < 5; i++) {
      final x = size.width * (0.1 + i * 0.22);
      canvas.drawLine(
        Offset(x, -20),
        Offset(x + size.width * 0.1, size.height + 20),
        minorRoadPaint,
      );
    }

    final mainPath = Path()
      ..moveTo(-10, size.height * 0.75)
      ..quadraticBezierTo(
        size.width * 0.35,
        size.height * 0.55,
        size.width * 0.55,
        size.height * 0.68,
      )
      ..quadraticBezierTo(
        size.width * 0.78,
        size.height * 0.84,
        size.width + 10,
        size.height * 0.55,
      );
    canvas.drawPath(mainPath, roadPaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
