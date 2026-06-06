import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:just_audio/just_audio.dart' as ja;

class AudioPlayerService {
  final _player = ja.AudioPlayer();
  String? _currentToken;
  VoidCallback? _onComplete;
  StreamSubscription? _stateSub;

  String? get currentToken => _currentToken;

  bool isPlaying(String token) =>
      _currentToken == token && _player.playing;

  Future<bool> play(
    String filePath,
    String token,
    VoidCallback onComplete,
  ) async {
    await stop();
    final file = File(filePath);
    if (!file.existsSync()) return false;

    _currentToken = token;
    _onComplete = onComplete;

    try {
      await _player.setFilePath(filePath);
      _stateSub?.cancel();
      _stateSub = _player.playerStateStream.listen((state) {
        if (state.processingState == ja.ProcessingState.completed) {
          _handleComplete();
        }
      });
      _player.play();
      return true;
    } catch (_) {
      _reset();
      return false;
    }
  }

  Future<void> stop() async {
    if (_currentToken == null) return;
    await _player.stop();
    _handleComplete();
  }

  void _handleComplete() {
    final cb = _onComplete;
    _reset();
    cb?.call();
  }

  void _reset() {
    _currentToken = null;
    _onComplete = null;
  }

  void dispose() {
    _stateSub?.cancel();
    _player.dispose();
  }
}
