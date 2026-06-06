import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/services/audio_player_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('AudioPlayerService', () {
    test('初始状态 isPlaying 返回 false', () {
      final service = AudioPlayerService();
      expect(service.isPlaying('any-token'), false);
      service.dispose();
    });

    test('currentToken 初始为 null', () {
      final service = AudioPlayerService();
      expect(service.currentToken, isNull);
      service.dispose();
    });

    test('play 不存在的文件返回 false', () async {
      final service = AudioPlayerService();
      final result = await service.play(
        '/nonexistent/file.m4a',
        'token-1',
        () {},
      );
      expect(result, false);
      expect(service.isPlaying('token-1'), false);
      service.dispose();
    });
  });
}
