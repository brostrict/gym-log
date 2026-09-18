import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import 'body_api.dart';

/// 填身体资料（身高 / 出生年 / 性别）。
///
/// 这三项**不直接展示，只参与计算**：身高算 BMI 和腰高比，
/// 出生年算年龄（进体脂率估算和 BMR），性别两个公式都要。
///
/// 所以这一页的措辞要讲清楚**填了能得到什么**——
/// 否则用户会觉得「又要我填资料」而直接跳过。
Future<void> showProfileSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (_) => const _ProfileSheet(),
  );
}

class _ProfileSheet extends ConsumerStatefulWidget {
  const _ProfileSheet();

  @override
  ConsumerState<_ProfileSheet> createState() => _ProfileSheetState();
}

class _ProfileSheetState extends ConsumerState<_ProfileSheet> {
  final _heightCtrl = TextEditingController();
  final _yearCtrl = TextEditingController();
  int _gender = 0;

  bool _loaded = false;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _heightCtrl.dispose();
    _yearCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final profile = ref.watch(bodyProfileProvider);

    // 只灌一次初值——每次 build 都覆盖的话，用户改到一半会被打回去
    final data = profile.asData?.value;
    if (!_loaded && data != null) {
      _loaded = true;
      _heightCtrl.text =
          data.heightCm == null ? '' : _trim(data.heightCm!);
      _yearCtrl.text = data.birthYear?.toString() ?? '';
      _gender = data.gender ?? 0;
    }

    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('身体资料', style: theme.textTheme.titleLarge),
              const SizedBox(height: 4),
              Text(
                '用来推导 BMI、体脂率估算、基础代谢率和腰高比。\n'
                '不填也能用 App，只是这几项算不出来。',
                style: theme.textTheme.bodySmall,
              ),
              const SizedBox(height: 20),

              // ---------- 身高 ----------
              TextField(
                controller: _heightCtrl,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d*')),
                ],
                decoration: const InputDecoration(
                  labelText: '身高',
                  suffixText: 'cm',
                  helperText: '80–250',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),

              // ---------- 出生年 ----------
              //
              // 用「出生年」而不是「年龄」：年龄每年都要改一次，
              // 而用户不会记得回来改。存年份的话它自己会变对。
              TextField(
                controller: _yearCtrl,
                keyboardType: TextInputType.number,
                inputFormatters: [
                  FilteringTextInputFormatter.digitsOnly,
                  LengthLimitingTextInputFormatter(4),
                ],
                decoration: const InputDecoration(
                  labelText: '出生年份',
                  helperText: '只用来算年龄，不显示',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),

              // ---------- 性别 ----------
              //
              // 两个公式都要它，而且不是可选的：Deurenberg 里性别差 10.8 个百分点，
              // Mifflin-St Jeor 里差 166 kcal——都不是可以「用个默认值」的量级
              Text('性别', style: theme.textTheme.labelMedium),
              const SizedBox(height: 6),
              Wrap(
                spacing: 8,
                children: [
                  for (final g in const [(1, '男'), (2, '女')])
                    ChoiceChip(
                      label: Text(g.$2),
                      selected: _gender == g.$1,
                      onSelected: (_) => setState(() => _gender = g.$1),
                    ),
                ],
              ),

              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!,
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.error)),
              ],

              const SizedBox(height: 20),
              FilledButton(
                onPressed: _saving ? null : _save,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(52),
                ),
                child: Text(_saving ? '保存中…' : '保存'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _save() async {
    final height = double.tryParse(_heightCtrl.text.trim());
    final year = int.tryParse(_yearCtrl.text.trim());

    if (height == null && year == null && _gender == 0) {
      setState(() => _error = '至少填一项');
      return;
    }

    setState(() {
      _saving = true;
      _error = null;
    });

    try {
      await ref.read(bodyApiProvider).updateBodyProfile(
            // 0 表示「没选」——不传，而不是传 0 把服务端的值改掉
            gender: _gender == 0 ? null : _gender,
            birthYear: year,
            heightCm: height,
          );
      if (!mounted) return;
      // 推导值依赖这三个字段，全部重取
      ref.invalidate(bodyProfileProvider);
      ref.invalidate(derivedMetricsProvider);
      Navigator.of(context).pop();
    } on ApiException catch (e) {
      // 服务端的校验文案是给用户看的（「身高应在 80–250 cm 之间」），直接显示
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
}
