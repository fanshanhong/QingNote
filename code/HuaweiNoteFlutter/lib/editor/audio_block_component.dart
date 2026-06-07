import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../services/audio_player_service.dart';
import '../theme.dart';

class AudioBlockKeys {
  const AudioBlockKeys._();
  static const String type = 'audio';
  static const String fileName = 'file_name';
  static const String durationMs = 'duration_ms';
}

Node audioNode({
  required String fileName,
  required int durationMs,
}) {
  return Node(
    type: AudioBlockKeys.type,
    attributes: {
      AudioBlockKeys.fileName: fileName,
      AudioBlockKeys.durationMs: durationMs,
    },
  );
}

class AudioBlockComponentBuilder extends BlockComponentBuilder {
  AudioBlockComponentBuilder({
    required this.noteId,
    required this.audioPlayer,
    required this.editable,
    required this.onDelete,
    required this.basePath,
    super.configuration,
  });

  final int noteId;
  final AudioPlayerService audioPlayer;
  final bool editable;
  final void Function(Node node) onDelete;
  final String basePath;

  @override
  BlockComponentWidget build(BlockComponentContext blockComponentContext) {
    final node = blockComponentContext.node;
    return AudioBlockComponentWidget(
      key: node.key,
      node: node,
      configuration: configuration,
      noteId: noteId,
      audioPlayer: audioPlayer,
      editable: editable,
      onDelete: onDelete,
      basePath: basePath,
    );
  }

  @override
  BlockComponentValidate get validate =>
      (node) => node.attributes[AudioBlockKeys.fileName] is String;
}

class AudioBlockComponentWidget extends BlockComponentStatefulWidget {
  const AudioBlockComponentWidget({
    super.key,
    required super.node,
    super.configuration = const BlockComponentConfiguration(),
    required this.noteId,
    required this.audioPlayer,
    required this.editable,
    required this.onDelete,
    required this.basePath,
  });

  final int noteId;
  final AudioPlayerService audioPlayer;
  final bool editable;
  final void Function(Node node) onDelete;
  final String basePath;

  @override
  State<AudioBlockComponentWidget> createState() =>
      _AudioBlockComponentWidgetState();
}

class _AudioBlockComponentWidgetState
    extends State<AudioBlockComponentWidget> {
  bool _isPlaying = false;

  String get _fileName =>
      widget.node.attributes[AudioBlockKeys.fileName] as String? ?? '';

  int get _durationMs =>
      widget.node.attributes[AudioBlockKeys.durationMs] as int? ?? 0;

  String get _filePath =>
      '${widget.basePath}/notes/${widget.noteId}/audio/$_fileName';

  String get _token => '${widget.noteId}_$_fileName';

  @override
  void dispose() {
    if (widget.audioPlayer.isPlaying(_token)) {
      widget.audioPlayer.stop();
    }
    super.dispose();
  }

  void _togglePlay() async {
    if (_isPlaying) {
      await widget.audioPlayer.stop();
      return;
    }
    final ok = await widget.audioPlayer.play(_filePath, _token, () {
      if (mounted) setState(() => _isPlaying = false);
    });
    if (ok && mounted) {
      setState(() => _isPlaying = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.bgWindow,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.divider),
      ),
      child: Row(
        children: [
          GestureDetector(
            onTap: _togglePlay,
            child: Container(
              width: 36,
              height: 36,
              decoration: const BoxDecoration(
                color: AppColors.primary,
                shape: BoxShape.circle,
              ),
              child: Icon(
                _isPlaying ? Icons.pause : Icons.play_arrow,
                color: Colors.white,
                size: 20,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              '录音 ${_formatDuration(_durationMs)}',
              style: const TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary,
              ),
            ),
          ),
          if (widget.editable)
            GestureDetector(
              onTap: () => widget.onDelete(widget.node),
              child: const Padding(
                padding: EdgeInsets.all(4),
                child: Icon(Icons.close, size: 18, color: AppColors.textHint),
              ),
            ),
        ],
      ),
    );
  }

  String _formatDuration(int ms) {
    final total = ms ~/ 1000;
    final mm = total ~/ 60;
    final ss = total % 60;
    return '${mm.toString().padLeft(2, '0')}:${ss.toString().padLeft(2, '0')}';
  }
}
