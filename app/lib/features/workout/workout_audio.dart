import 'dart:async';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_tts/flutter_tts.dart';

/// 跟练的听觉提示 —— 提示音 + 语音播报 + 震动。
///
/// # ★ 整个类的存在意义：不打断用户的音乐
///
/// 健身房里绝大多数人戴耳机听歌训练。**如果提示音每 90 秒打断一次音乐，
/// 用户会直接卸载**——这不是优化项，是能否使用的前提（TIMER-SPEC 3.4.1）。
///
/// ## 三条要求，缺一不可
///
/// | 要求 | 违反的后果 |
/// |---|---|
/// | 不暂停其他 App 的音乐 | 每 90 秒音乐停一次，无法忍受 |
/// | 不降低其他 App 的音量 | 音乐一直在 duck，忽大忽小 |
/// | **不夺取音频焦点** | 这是根本原因——夺取焦点必然导致系统去处理其他 App |
///
/// ## Android：音频属性是 per-player 的
///
/// ```dart
/// contentType: AndroidContentType.sonification,
/// usageType:   AndroidUsageType.assistanceSonification,
/// audioFocus:  AndroidAudioFocus.none,   // ← 最重要的一个
/// ```
///
/// `assistanceSonification` 让系统把这个音频当作**辅助提示音**，
/// 与媒体流混音而非互斥。
///
/// `audioFocus: none` 不是「音量小一点」，而是**根本不发起焦点请求**——
/// 已核对 audioplayers_android 5.3.0 的 `FocusManager.kt`：
/// `AUDIOFOCUS_NONE` 时连 `OnAudioFocusChangeListener` 都不注册。
/// 低延迟模式（SoundPool）同样按 AudioAttributes 分桶，属性依然生效。
///
/// ## ⚠️ 三条「文档没说、但会踩」的坑（已逐一核对源码）
///
/// **1. iOS 的 `ambient` 配 `mixWithOthers` 会直接断言失败。**
/// `AudioContextIOS` 的构造函数里有 assert：`mixWithOthers` 只能配
/// `playback` / `playAndRecord` / `multiRoute`；`ambient` 本身就隐含混音，
/// 不能再显式声明。这里**故意不用 `ambient`**，理由见下面的注释。
///
/// **2. `flutter_tts` 的 `speak()` 有个 Android 专有的 `focus` 参数，
/// 默认 false。** 也就是说 **TTS 默认不抢焦点**——但一旦有人写成
/// `speak(text, focus: true)`，它会请求 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，
/// 音乐立刻被压低。所以这里**显式传 false**，把默认值变成明文契约。
///
/// **3. `setAudioAttributesForNavigation()` 不收任何参数。**
/// 它内部写死 `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + `CONTENT_TYPE_SPEECH`，
/// 是插件唯一提供的「播报类」音频属性入口。
class WorkoutAudio {
  WorkoutAudio();

  /// 每个音效一个播放器。
  ///
  /// **不是一个播放器换三个音源。** SoundPool 在切换音源时会
  /// `unload` 掉上一个（见 `SoundPoolPlayer.release()`），
  /// 下一个音又得重新 `load()` —— 而 `load()` 是异步的，
  /// `play()` 要等 `prepared=true` 才真的出声。
  ///
  /// 三个播放器各自常驻一个已加载的音源，代价是三个小对象，
  /// 换来每次提示音都是**零加载延迟**。
  final Map<String, AudioPlayer> _players = {};

  FlutterTts? _tts;

  Future<void>? _playerInit;
  Future<void>? _ttsInit;

  /// 三个音效。改这里就要同步改 `assets/audio/`。
  static const _sounds = ['warn.wav', 'go.wav', 'tick.wav'];

  bool _enabled = true;

  /// 语音播报**默认关闭**（TIMER-SPEC 3.4）。
  ///
  /// 主通道是提示音 + 震动：不需要权限、不需要网络、不挑设备。
  /// 语音是用户主动打开的增强项——健身房本来就嘈杂，
  /// 而且部分设备根本没有中文语音引擎。
  bool _voiceEnabled = false;

  bool get soundEnabled => _enabled;
  bool get voiceEnabled => _voiceEnabled;

  /// 语音播报此刻是否真的会出声。设备没中文引擎时静默降级。
  bool get voiceReady => _voiceEnabled && _tts != null;

  /// **只准备播放器。**
  ///
  /// 语音播报和提示音分开初始化，而且**默认压根不初始化 TTS**——
  /// 一来语音默认关闭，二来 `flutter_tts` 的插件在引擎就绪前会挂起
  /// 所有方法调用，设备没装语音引擎时那个 Future 可能永远不完成。
  /// 让提示音去 await 它，结果是**没装 TTS 的手机连提示音都不响**。
  /// 提示音是核心功能（AC-4-8），绝不能被语音拖垮。
  Future<void> init() async {
    await _ensurePlayer();
  }

  Future<void> _ensurePlayer() => _playerInit ??= _initPlayer();

  Future<void> _ensureTts() => _ttsInit ??= _initTts();

  /// 开关语音播报。**首次打开时才初始化 TTS 引擎**——
  /// 默认关闭的用户一辈子不用付这个初始化代价。
  Future<void> setVoiceEnabled(bool enabled) async {
    _voiceEnabled = enabled;
    if (enabled) await _ensureTts();
  }

  /// ★ **音频隔离的全部要害就在这三行**（见类注释）。
  ///
  /// 每个播放器都要单独设——AudioContext 是 per-player 的。
  static final _context = AudioContext(
    android: AudioContextAndroid(
      isSpeakerphoneOn: false,
      stayAwake: false,
      // 「这是什么声音」——合成提示音，不是音乐
      contentType: AndroidContentType.sonification,
      // 「为什么放这个声音」——辅助提示
      usageType: AndroidUsageType.assistanceSonification,
      // ★ 不申请音频焦点。
      // 申请焦点 = 告诉系统「我要独占音频」，系统就会去暂停其他 App。
      audioFocus: AndroidAudioFocus.none,
    ),
    iOS: AudioContextIOS(
      // ★ 为什么不是 ambient：
      //
      // `ambient` 会被**静音拨片**静音，而音乐 App 用的是 `playback`，
      // 静音拨片对它无效。结果是：用户开着静音听歌，音乐正常播放，
      // 我们的提示音却一声不响——比打断音乐更糟，因为它是静默失效。
      //
      // `playback` + `mixWithOthers` 与音乐 App 同档：都能出声、都不被
      // 静音拨片影响、且彼此混音不互斥。
      category: AVAudioSessionCategory.playback,
      options: const {AVAudioSessionOptions.mixWithOthers},
    ),
  );

  Future<void> _initPlayer() async {
    for (final sound in _sounds) {
      final player = AudioPlayer();

      await player.setAudioContext(_context);

      // 低延迟模式：提示音要求「刚好在那一刻响」，
      // 默认播放器缓冲会带来几十到几百毫秒延迟。
      // SoundPool 内部按 AudioAttributes 分桶，上面的隔离配置依然生效。
      await player.setPlayerMode(PlayerMode.lowLatency);
      await player.setReleaseMode(ReleaseMode.stop);

      // ★ 预加载音源。
      //
      // `setSource` 会触发 `soundPool.load()`；之后再 `resume()` 就是直接出声，
      // 不用等加载。**不预加载的话，一个会话里的第一次播放要慢半拍**——
      // 真机实测：首次提示音比预定时刻晚了 461ms，
      // 而同一会话后续的提示音只差 15ms。
      //
      // 对一个「还剩 3 秒」的提示音来说，半秒的偏差是致命的。
      await player.setSource(AssetSource('audio/$sound'));

      _players[sound] = player;
    }

    // 把输出通路也预热掉：播放器对象建好不等于音频通路建好，
    // 第一条 AudioTrack 要等真正出声才会创建（含 HAL 流配置）。
    // 这一下静音播放发生在用户还在看屏幕的时候，不在倒计时里。
    await _warmUp();
  }

  /// 静音跑一小段，把音频输出通路建起来。
  ///
  /// 音量设 0 再恢复——**用户听不到任何声音**，
  /// 但 `AudioTrack` 和 HAL 流已经在这次调用里建好了。
  Future<void> _warmUp() async {
    final player = _players['tick.wav'];
    if (player == null) return;
    try {
      await player.setVolume(0);
      await player.resume();
      // 等出声之后再停：立刻 stop 可能赶在 AudioTrack 创建之前，
      // 那样就白预热了
      await Future.delayed(const Duration(milliseconds: 120));
      await player.stop();
    } catch (e) {
      debugPrint('音频通路预热失败（不影响使用）：$e');
    } finally {
      // ⚠️ 音量一定要恢复。忘了这一步，之后所有提示音都是静音的——
      // 而且因为「静音」看起来和「正常」一模一样，极难排查。
      await player.setVolume(1);
    }
  }

  Future<void> _initTts() async {
    try {
      final tts = FlutterTts();
      await tts.setLanguage('zh-CN');
      // 语速稍快：播报不该拖太久盖住音乐
      await tts.setSpeechRate(0.5);
      await tts.setVolume(1.0);

      // Android：把 TTS 的音频属性也设成「播报类」。
      //
      // 插件的 onMethodCall 在引擎就绪前会挂起所有调用
      // （`ttsStatus == null` → 入队），所以这里不会因为引擎没初始化好而失效。
      await tts.setAudioAttributesForNavigation();

      // iOS：AVAudioSession 的 category 是**整个 App 全局**的，不是每个
      // 播放器各自一份。所以 TTS 必须和上面的 AudioContextIOS 保持一致——
      // 否则谁后设置谁生效，可能把提示音一起降级成被静音拨片静音。
      await tts.setIosAudioCategory(
        IosTextToSpeechAudioCategory.playback,
        [IosTextToSpeechAudioCategoryOptions.mixWithOthers],
      );

      // 刻意**不开** awaitSpeakCompletion：开了之后 speak() 要等整句读完
      // 才 resolve，中途引擎异常就会挂住调用链。截断问题由 _speak 里的
      // stop() 解决，不需要它。

      _tts = tts;
    } catch (e) {
      // TTS 不是必需的——设备没装中文语音引擎时静默降级，
      // 提示音和震动仍然有效。`_tts` 保持 null，`voiceReady` 自然为 false。
      debugPrint('TTS 初始化失败，语音播报已禁用：$e');
    }
  }

  // ==================================================================
  // 提示音（TIMER-SPEC 3.4 的分频编码）
  // ==================================================================

  /// 剩余 3 秒：三声短促中音，每 400ms 一声
  Future<void> warnCountdown() async {
    if (!_enabled) return;
    for (var i = 0; i < 3; i++) {
      await _play('warn.wav');
      if (i < 2) await Future.delayed(const Duration(milliseconds: 400));
    }
    HapticFeedback.selectionClick();
  }

  /// 归零：一声长高音
  Future<void> restFinished() async {
    if (!_enabled) return;
    await _play('go.wav');
    // 震动兜底：静音模式下听不到提示音，震动是唯一的反馈（AC-4-5）
    HapticFeedback.heavyImpact();
  }

  /// 休息过半：一声短低音，用来宣告「你可以开始准备了」
  Future<void> halfwayHint() async {
    if (!_enabled) return;
    await _play('tick.wav');
  }

  /// 时长类动作组内的间隔提示音。
  ///
  /// 和 [halfwayHint] 用的是同一个音，但**不复用它**：这两个方法的语义
  /// 完全不同（一个是「休息过半」，一个是「撑了 N 秒了」），
  /// 将来想给组内换一个音时不该牵动休息那边。
  ///
  /// 语音关闭时它就是**唯一**的进度信号——所以必须有，不能只靠 TTS。
  Future<void> holdTick() async {
    if (!_enabled) return;
    await _play('tick.wav');
  }

  Future<void> _play(String asset) async {
    if (!_enabled) return;
    try {
      await _ensurePlayer();
      final player = _players[asset];
      if (player == null) return;
      // 先 stop 归零播放位置，再 resume —— 每次都从头播。
      // 用 resume 不用 play(AssetSource)：音源在 init 时就设好了，
      // 再设一次会触发重新加载，白扔掉预加载的成果。
      await player.stop();
      await player.resume();
    } catch (e) {
      debugPrint('播放提示音失败：$e');
    }
  }

  // ==================================================================
  // 语音播报
  // ==================================================================

  /// 播报下一组。休息归零时调用（M4-B-5）。
  Future<void> announceNextSet({
    required String exerciseName,
    required int setNumber,
    required int totalSets,
    String? weightLabel,
    String? repsLabel,
  }) async {
    if (!_enabled || !voiceReady) return;

    final parts = <String>[
      exerciseName,
      '第 $setNumber 组，共 $totalSets 组',
      ?weightLabel,
      if (repsLabel != null) '$repsLabel 次',
    ];
    await _speak(parts.join('，'));
  }

  /// 播报动作切换。
  Future<void> announceNextExercise(String exerciseName, int totalSets) async {
    if (!_enabled || !voiceReady) return;
    await _speak('下一个动作，$exerciseName，共 $totalSets 组');
  }

  /// 播报训练完成。
  Future<void> announceSessionDone() async {
    if (!_enabled || !voiceReady) return;
    await _speak('训练完成');
  }

  /// 组内倒计时播报：「还剩 20 秒」。
  ///
  /// 撑平板支撑的人脸朝下、看不见屏幕，语音是这时候唯一有效的信息通道——
  /// 但语音默认关闭，所以调用方必须配一声 [holdTick] 作兜底。
  Future<void> announceHoldRemaining(int seconds) async {
    if (!_enabled || !voiceReady) return;
    await _speak('还剩 $seconds 秒');
  }

  /// 组内超时播报：「已超 10 秒」。
  Future<void> announceHoldOvertime(int seconds) async {
    if (!_enabled || !voiceReady) return;
    await _speak('已超 $seconds 秒');
  }

  Future<void> _speak(String text) async {
    try {
      await _tts?.stop();
      // ★ 显式传 focus: false —— 见类注释的坑 2。
      // 这一个 bool 就是「语音播报会不会压低音乐」的开关。
      await _tts?.speak(text, focus: false);
    } catch (e) {
      debugPrint('语音播报失败：$e');
    }
  }

  // ==================================================================
  // 开关与释放
  // ==================================================================

  void setSoundEnabled(bool enabled) => _enabled = enabled;

  Future<void> dispose() async {
    _enabled = false;
    _voiceEnabled = false;
    // 等初始化落地再释放：否则 init 还没跑完就 dispose，
    // 正在创建的那个播放器会漏掉
    await _playerInit;
    await _ttsInit;
    for (final player in _players.values) {
      await player.dispose();
    }
    await _tts?.stop();
    _players.clear();
    _tts = null;
    _playerInit = null;
    _ttsInit = null;
  }
}
