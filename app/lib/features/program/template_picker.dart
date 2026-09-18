import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_exception.dart';
import 'program_api.dart';
import 'program_models.dart';

/// 计划模板库 —— 没有进行中的计划时显示。
///
/// 这是**新用户的第一屏**。没有它的话，用户注册完进来看到
/// 一句「还没有进行中的计划」就不知道下一步该干什么了。
/// M3-A 的六个内置模板就是为这一刻准备的：选一个，开始练。
class TemplatePicker extends ConsumerWidget {
  const TemplatePicker({super.key, required this.onCreated});
  final VoidCallback onCreated;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final templates = ref.watch(templatesProvider);

    return templates.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.cloud_off,
                  size: 48, color: Theme.of(context).colorScheme.error),
              const SizedBox(height: 12),
              Text(e is ApiException ? e.message : '$e',
                  textAlign: TextAlign.center),
              const SizedBox(height: 16),
              OutlinedButton(
                onPressed: () => ref.invalidate(templatesProvider),
                child: const Text('重试'),
              ),
            ],
          ),
        ),
      ),
      data: (list) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SizedBox(height: 8),
          Text('选一个计划开始',
              style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 6),
          Text(
            '模板是「零配置」的起点。创建之后可以随意改成自己的安排。',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          const SizedBox(height: 20),
          ...list.map((t) => _TemplateCard(
                template: t,
                onPick: () => _create(context, ref, t),
              )),
        ],
      ),
    );
  }

  Future<void> _create(BuildContext context, WidgetRef ref, ProgramTemplate t) async {
    // ---------- 让用户确认开始日期 ----------
    //
    // 开始日期决定「今天算第几周」，直接默认成今天会让想下周开始的人
    // 算错周期。而且创建之后**没有地方能改它**（计划结构编辑不涉及元信息），
    // 所以在这里问一次是必要的。
    final now = DateTime.now();
    final startDate = await showDialog<DateTime>(
      context: context,
      builder: (ctx) => _StartDateDialog(template: t, initial: now),
    );
    if (startDate == null || !context.mounted) return;

    try {
      await ref.read(programApiProvider).createFromTemplate(
            templateCode: t.code,
            startDate: startDate,
          );
      // 计划建好了 → 首页要重新拉「今天练什么」
      ref.invalidate(templatesProvider);
      onCreated();
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.message)),
        );
      }
    }
  }
}

class _TemplateCard extends StatelessWidget {
  const _TemplateCard({required this.template, required this.onPick});
  final ProgramTemplate template;
  final VoidCallback onPick;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: onPick,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(template.name,
                        style: theme.textTheme.titleMedium),
                  ),
                  Chip(
                    label: Text(template.goalLabel),
                    visualDensity: VisualDensity.compact,
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(template.description,
                  style: theme.textTheme.bodySmall,
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 4,
                children: [
                  _Tag(icon: Icons.calendar_today,
                      text: template.frequencyLabel),
                  if (template.levelLabel.isNotEmpty)
                    _Tag(icon: Icons.signal_cellular_alt,
                        text: template.levelLabel),
                  if (template.estimatedMinutes != null)
                    _Tag(icon: Icons.timer,
                        text: '约 ${template.estimatedMinutes} 分钟'),
                ],
              ),
              if (template.equipmentSummary != null) ...[
                const SizedBox(height: 8),
                Text('需要：${template.equipmentSummary}',
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.outline)),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _Tag extends StatelessWidget {
  const _Tag({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: theme.colorScheme.onSurfaceVariant),
        const SizedBox(width: 4),
        Text(text, style: theme.textTheme.labelSmall),
      ],
    );
  }
}

/// 创建前确认开始日期。
class _StartDateDialog extends StatefulWidget {
  const _StartDateDialog({required this.template, required this.initial});
  final ProgramTemplate template;
  final DateTime initial;

  @override
  State<_StartDateDialog> createState() => _StartDateDialogState();
}

class _StartDateDialogState extends State<_StartDateDialog> {
  late DateTime _date = widget.initial;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.template.name),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('从哪天开始？'),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () async {
              final picked = await showDatePicker(
                context: context,
                initialDate: _date,
                // 允许往前选一周：用户可能想把已经练了几天的那一轮补上
                firstDate: DateTime.now().subtract(const Duration(days: 7)),
                lastDate: DateTime.now().add(const Duration(days: 90)),
              );
              if (picked != null) setState(() => _date = picked);
            },
            icon: const Icon(Icons.edit_calendar),
            label: Text('${_date.year}-${_p(_date.month)}-${_p(_date.day)}'),
          ),
          const SizedBox(height: 12),
          Text(
            '这个日期决定「今天算第几周」。创建之后暂时不能修改。',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, _date),
          child: const Text('创建计划'),
        ),
      ],
    );
  }

  static String _p(int n) => n.toString().padLeft(2, '0');
}
