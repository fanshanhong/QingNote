import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:record/record.dart';
import '../../theme.dart';

class AudioRecordingResult {
  final String filePath;
  final int durationMs;
  const AudioRecordingResult(this.filePath, this.durationMs);
}

Future<AudioRecordingResult?> showAudioRecordingSheet(
  BuildContext context, {
  required String targetFilePath,
}) async {
  return showModalBottomSheet<AudioRecordingResult>(
    context: context,
    isDismissible: false,
    enableDrag: false,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (ctx) => _AudioRecordingContent(targetFilePath: targetFilePath),
  );
}

class _AudioRecordingContent extends StatefulWidget {
  final String targetFilePath;
  const _AudioRecordingContent({required this.targetFilePath});

  @override
  State<_AudioRecordingContent> createState() => _AudioRecordingContentState();
}

class _AudioRecordingContentState extends State<_AudioRecordingContent> {
  final _recorder = AudioRecorder();
  Timer? _timer;
  int _elapsedMs = 0;
  bool _isRecording = false;
  bool _terminated = false;

  @override
  void initState() {
    super.initState();
    _startRecording();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _recorder.dispose();
    super.dispose();
  }

  Future<void> _startRecording() async {
    final hasPermission = await _recorder.hasPermission();
    if (!hasPermission) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('需要麦克风权限')),
        );
        Navigator.pop(context);
      }
      return;
    }

    try {
      await _recorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 64000,
          sampleRate: 44100,
        ),
        path: widget.targetFilePath,
      );
      setState(() => _isRecording = true);
      _timer = Timer.periodic(const Duration(milliseconds: 500), (_) {
        setState(() => _elapsedMs += 500);
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('录音启动失败')),
        );
        Navigator.pop(context);
      }
    }
  }

  Future<void> _stop() async {
    if (_terminated) return;
    _terminated = true;
    _timer?.cancel();
    final path = await _recorder.stop();
    if (mounted && path != null) {
      Navigator.pop(
        context,
        AudioRecordingResult(path, _elapsedMs),
      );
    }
  }

  Future<void> _cancel() async {
    if (_terminated) return;
    _terminated = true;
    _timer?.cancel();
    await _recorder.stop();
    final file = File(widget.targetFilePath);
    if (file.existsSync()) file.deleteSync();
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.mic, size: 48, color: AppColors.danger),
            const SizedBox(height: 12),
            Text(
              _formatTime(_elapsedMs),
              style: const TextStyle(
                fontSize: 32,
                fontWeight: FontWeight.w300,
                color: AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _cancel,
                    child: const Text('取消'),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isRecording ? _stop : null,
                    icon: const Icon(Icons.stop),
                    label: const Text('停止录音'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.danger,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  String _formatTime(int ms) {
    final total = ms ~/ 1000;
    final mm = total ~/ 60;
    final ss = total % 60;
    return '${mm.toString().padLeft(2, '0')}:${ss.toString().padLeft(2, '0')}';
  }
}
