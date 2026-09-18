import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// 声音偏好 —— 提示音和语音播报两个开关。
///
/// # 为什么要落盘
///
/// `workoutAudioProvider` 是 `Provider`，离开跟练页就 dispose，
/// 而开关状态存在 `WorkoutAudio` 的实例字段里。**不持久化的话，
/// 每次进跟练页都要重新打开一遍语音**——用户开过一次就该记住。
///
/// # 和 M9 的关系
///
/// REQUIREMENTS 的 M9-3 要求「提示音开关、语音播报开关、震动开关」（Must），
/// 规划的是一个完整的设置页。这里先落最小可用的一层：
/// **只有存储 + 跟练页入口**，设置页仍然留给 M9。
/// 到时候把 UI 挪过去、这里保留成 provider 即可。
class SoundSettings {
  const SoundSettings({
    required this.soundEnabled,
    required this.voiceEnabled,
  });

  /// 提示音（含震动）。默认开——它是主通道，不需要权限也不挑设备
  final bool soundEnabled;

  /// 语音播报。**默认关**（TIMER-SPEC 3.4）：
  /// 健身房环境嘈杂，且部分设备没有中文语音引擎
  final bool voiceEnabled;

  SoundSettings copyWith({bool? soundEnabled, bool? voiceEnabled}) {
    return SoundSettings(
      soundEnabled: soundEnabled ?? this.soundEnabled,
      voiceEnabled: voiceEnabled ?? this.voiceEnabled,
    );
  }
}

const _kSoundKey = 'sound.enabled';
const _kVoiceKey = 'sound.voice';

class SoundSettingsController extends Notifier<SoundSettings> {
  @override
  SoundSettings build() {
    // 先给默认值，异步读盘后覆盖。
    // 不阻塞启动——读盘是毫秒级的事，但没必要让首帧等它。
    _load();
    return const SoundSettings(soundEnabled: true, voiceEnabled: false);
  }

  Future<void> _load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      state = SoundSettings(
        soundEnabled: prefs.getBool(_kSoundKey) ?? true,
        voiceEnabled: prefs.getBool(_kVoiceKey) ?? false,
      );
    } catch (_) {
      // 读不到就用默认值。偏好设置读不出来不该让跟练页崩掉
    }
  }

  Future<void> setSoundEnabled(bool enabled) async {
    state = state.copyWith(soundEnabled: enabled);
    await _persist(_kSoundKey, enabled);
  }

  Future<void> setVoiceEnabled(bool enabled) async {
    state = state.copyWith(voiceEnabled: enabled);
    await _persist(_kVoiceKey, enabled);
  }

  Future<void> _persist(String key, bool value) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(key, value);
    } catch (_) {
      // 写盘失败只影响「下次开 App 记不记得」，不影响本次使用
    }
  }
}

final soundSettingsProvider =
    NotifierProvider<SoundSettingsController, SoundSettings>(
        SoundSettingsController.new);
