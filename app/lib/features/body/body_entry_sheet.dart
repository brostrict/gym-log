import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import 'body_api.dart';
import 'body_models.dart';

/// 打开录入表单。
///
/// 用底部弹出面板而不是整页：录入只有 1–3 个字段，
/// 整页跳转会让人以为「填错了回不去」，而这是个一天可能用一次的动作。
Future<void> showBodyEntrySheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,   // 键盘弹起时能顶上去，否则输入框被挡住
    builder: (_) => const _BodyEntrySheet(),
  );
}

/// **整个表单由服务端的元数据生成**，客户端不硬编码任何量程、单位、部位。
///
/// 理由见 `body_models.dart` 开头——同一个知识两头写，失效方式很不友好
/// （前端拦住后端接受的输入，或者反过来）。
class _BodyEntrySheet extends ConsumerStatefulWidget {
  const _BodyEntrySheet();

  @override
  ConsumerState<_BodyEntrySheet> createState() => _BodyEntrySheetState();
}

class _BodyEntrySheetState extends ConsumerState<_BodyEntrySheet> {
  String _metricType = 'WEIGHT';
  String? _site;
  String? _condition;
  DateTime _measuredAt = DateTime.now();

  final _valueCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();
  final _deviceCtrl = TextEditingController();

  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _valueCtrl.dispose();
    _noteCtrl.dispose();
    _deviceCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final types = ref.watch(bodyMetricTypesProvider);

    return Padding(
      // 键盘高度——不加的话输入框被键盘顶住，用户看不到自己填了什么
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: types.when(
        loading: () => const SizedBox(
            height: 200, child: Center(child: CircularProgressIndicator())),
        error: (e, _) => SizedBox(
          height: 200,
          child: Center(child: Text('$e')),
        ),
        data: (list) => _form(context, theme, list),
      ),
    );
  }

  Widget _form(BuildContext context, ThemeData theme, List<BodyMetricType> types) {
    final type = types.firstWhere((t) => t.metricType == _metricType);
    // 换指标时旧部位可能不合法（WAIST 对体重没有意义）——
    // 这里过滤掉，而不是把一个非法值发给服务端换一个 400 回来
    final site = type.hasSites
        ? (type.sites.any((s) => s.site == _site)
            ? _site
            // 必填部位（围度）默认选第一个：不默认的话用户很容易
            // 忘了选，提交后被 60005 拦回来，白填一遍
            : (type.siteRequired && type.sites.isNotEmpty
                ? type.sites.first.site
                : null))
        : null;

    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('记录身体数据', style: theme.textTheme.titleLarge),
            const SizedBox(height: 16),

            // ---------- 指标 ----------
            DropdownButtonFormField<String>(
              initialValue: _metricType,
              decoration: const InputDecoration(
                labelText: '指标',
                border: OutlineInputBorder(),
              ),
              items: [
                for (final t in types)
                  DropdownMenuItem(value: t.metricType, child: Text(t.label)),
              ],
              onChanged: (v) => setState(() {
                _metricType = v!;
                _site = null;
                _condition = null;
                _error = null;
              }),
            ),
            const SizedBox(height: 12),

            // ---------- 部位（只有该指标有部位概念时才显示）----------
            if (type.hasSites) ...[
              DropdownButtonFormField<String>(
                initialValue: site,
                decoration: InputDecoration(
                  labelText: type.siteRequired ? '部位（必填）' : '部位（可选）',
                  border: const OutlineInputBorder(),
                ),
                items: [
                  if (!type.siteRequired)
                    const DropdownMenuItem(value: null, child: Text('不指定')),
                  for (final s in type.sites)
                    DropdownMenuItem(value: s.site, child: Text(s.label)),
                ],
                onChanged: (v) => setState(() => _site = v),
              ),
              const SizedBox(height: 12),
            ],

            // ---------- 数值 ----------
            TextField(
              controller: _valueCtrl,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d*')),
              ],
              decoration: InputDecoration(
                labelText: '数值',
                // 量程和单位都来自服务端——客户端不写第二份
                helperText: '${_trim(type.min)}–${_trim(type.max)} ${type.unit}',
                suffixText: type.unit,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),

            // ---------- 测量条件（只有该指标支持时）----------
            if (type.conditionSupported) ...[
              Text('测量条件', style: theme.textTheme.labelMedium),
              const SizedBox(height: 6),
              Wrap(
                spacing: 8,
                children: [
                  for (final c in _conditions)
                    ChoiceChip(
                      label: Text(c.$2),
                      selected: _condition == c.$1,
                      onSelected: (on) => setState(
                          () => _condition = on ? c.$1 : null),
                    ),
                ],
              ),
              // 体重和静息心率的条件写错，这个数就没意义了（METRICS 1.4）
              if (_metricType == 'WEIGHT' || _metricType == 'RESTING_HR') ...[
                const SizedBox(height: 4),
                Text(
                  '长期趋势要统一条件——晨起空腹和训练后能差 1kg 以上',
                  style: theme.textTheme.labelSmall
                      ?.copyWith(color: theme.colorScheme.outline),
                ),
              ],
              const SizedBox(height: 12),
            ],

            // ---------- 时间 ----------
            Row(
              children: [
                Expanded(
                  child: Text('测量时间：${_fmtTime(_measuredAt)}',
                      style: theme.textTheme.bodyMedium),
                ),
                TextButton(
                  onPressed: _pickTime,
                  child: const Text('修改'),
                ),
              ],
            ),

            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!,
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.error)),
            ],

            const SizedBox(height: 16),
            FilledButton(
              onPressed: _saving ? null : () => _save(type, site),
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(52),
              ),
              child: Text(_saving ? '保存中…' : '保存'),
            ),
          ],
        ),
      ),
    );
  }

  /// 与 `MetricCondition` 枚举一致。**这一份是可以硬编码的**——
  /// 它是枚举值不是量程，客户端总得知道显示哪几个选项；
  /// 而量程/单位/部位是「服务端也要用的知识」，那才不能两头写。
  static const _conditions = [
    ('FASTED', '晨起空腹'),
    ('POST_WORKOUT', '训练后'),
    ('BEFORE_BED', '睡前'),
    ('OTHER', '其他'),
  ];

  Future<void> _pickTime() async {
    final date = await showDatePicker(
      context: context,
      initialDate: _measuredAt,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_measuredAt),
    );
    if (!mounted) return;
    setState(() {
      _measuredAt = DateTime(
        date.year, date.month, date.day,
        time?.hour ?? _measuredAt.hour,
        time?.minute ?? _measuredAt.minute,
      );
    });
  }

  Future<void> _save(BodyMetricType type, String? site) async {
    final value = double.tryParse(_valueCtrl.text.trim());
    if (value == null) {
      setState(() => _error = '请填写数值');
      return;
    }
    // 客户端先拦一道只是**为了少一次往返**，服务端仍然会独立校验——
    // 它才是量程的权威（离线队列重放时客户端根本不在场）。
    if (value < type.min || value > type.max) {
      setState(() => _error =
          '${type.label}应在 ${_trim(type.min)}–${_trim(type.max)} ${type.unit} 之间');
      return;
    }

    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      await ref.read(bodyApiProvider).record(
            metricType: type.metricType,
            site: site,
            value: value,
            measuredAt: _measuredAt,
            condition: _condition,
            device: _deviceCtrl.text.trim(),
            note: _noteCtrl.text.trim(),
          );
      if (!mounted) return;

      // 这张图、这个指标的记录列表都要重取
      ref.invalidate(bodySeriesProvider);
      ref.invalidate(bodyRecordsProvider(type.metricType));
      Navigator.of(context).pop();
    } on ApiException catch (e) {
      // 服务端的报错文案是**给用户看的**（比如「围度必须指定部位」），
      // 直接显示比包装成「保存失败」有用得多
      setState(() {
        _saving = false;
        _error = e.message;
      });
    } catch (e) {
      setState(() {
        _saving = false;
        _error = '$e';
      });
    }
  }

  static String _trim(double v) =>
      v == v.roundToDouble() ? v.toInt().toString() : v.toString();

  static String _fmtTime(DateTime d) {
    String two(int n) => n.toString().padLeft(2, '0');
    return '${d.year}-${two(d.month)}-${two(d.day)} ${two(d.hour)}:${two(d.minute)}';
  }
}
