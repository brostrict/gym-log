import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/widgets/error_view.dart';
import 'exercise_api.dart';
import 'exercise_models.dart';

/// 动作库 —— **查找维度**，和统计维度分开。
///
/// > 对照竞品时想清楚的一件事：它们的肌群分类有 17 个，我们只有 6 个。
/// > 但我们的 6 个是**统计维度**（「这周胸练了几组」，分类太细每类都没数据），
/// > 而它们那 17 个是**查找维度**。
/// >
/// > 所以正确的做法不是把 6 改成 17，而是**补上查找这一维**。
/// > 这个页面就是那一维——肌群筛选仍然只有 6 个（外加两个非肌群分类），
/// > 但器械、动作模式、关键词都能筛。
class ExerciseLibraryScreen extends ConsumerWidget {
  const ExerciseLibraryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final filters = ref.watch(exerciseFiltersProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('动作库')),
      body: filters.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorView(
          error: e,
          onRetry: () => ref.invalidate(exerciseFiltersProvider),
        ),
        data: (f) => _Body(filters: f),
      ),
    );
  }
}

class _Body extends ConsumerWidget {
  const _Body({required this.filters});
  final ExerciseFilters filters;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final filter = ref.watch(exerciseFilterProvider);
    final list = ref.watch(exerciseListProvider(filter));
    final theme = Theme.of(context);
    final hasFilter =
        filter.muscle != null || filter.equipment != null || filter.movementPattern != null;

    return Column(
      children: [
        // ---------- 筛选项 ----------
        //
        // 三行横滚而不是一个「筛选」按钮 + 弹窗：
        // 筛选器本身要能**一眼看到有哪些选项**——用户不知道有「壶铃」这个分类时，
        // 不会去点一个按钮找它。
        _FilterRow(
          label: '部位',
          options: filters.muscles,
          selected: filter.muscle,
          onTap: (v) => ref.read(exerciseFilterProvider.notifier).toggleMuscle(v),
        ),
        _FilterRow(
          label: '器械',
          options: filters.equipment,
          selected: filter.equipment,
          onTap: (v) => ref.read(exerciseFilterProvider.notifier).toggleEquipment(v),
        ),
        _FilterRow(
          label: '模式',
          options: filters.movementPatterns,
          selected: filter.movementPattern,
          onTap: (v) => ref.read(exerciseFilterProvider.notifier).toggleMovement(v),
        ),

        // ---------- 结果计数 + 清空 ----------
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 8, 4),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  list.asData == null
                      ? '加载中…'
                      : '${list.value!.length} 个动作'
                          '${hasFilter ? '（已筛选）' : ''}',
                  style: theme.textTheme.labelMedium,
                ),
              ),
              if (hasFilter)
                TextButton(
                  onPressed: () => ref.read(exerciseFilterProvider.notifier).clear(),
                  style: TextButton.styleFrom(visualDensity: VisualDensity.compact),
                  child: const Text('清空筛选'),
                ),
            ],
          ),
        ),

        // ---------- 列表 ----------
        Expanded(
          child: list.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => ErrorView(
              error: e,
              onRetry: () => ref.invalidate(exerciseListProvider(filter)),
            ),
            data: (items) => items.isEmpty
                ? const Center(child: Text('没有符合条件的动作'))
                : ListView.separated(
                    itemCount: items.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (_, i) => _ExerciseTile(exercise: items[i]),
                  ),
          ),
        ),
      ],
    );
  }
}

/// 一行横滚的筛选 chips。
class _FilterRow extends StatelessWidget {
  const _FilterRow({
    required this.label,
    required this.options,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final List<FilterOption> options;
  final String? selected;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      height: 44,
      child: Row(
        children: [
          // 固定宽度的行标签——三行 chips 的起点因此对齐，扫视时不会跳
          SizedBox(
            width: 44,
            child: Padding(
              padding: const EdgeInsets.only(left: 16),
              child: Text(label, style: theme.textTheme.labelSmall),
            ),
          ),
          Expanded(
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              itemCount: options.length,
              separatorBuilder: (_, _) => const SizedBox(width: 6),
              itemBuilder: (_, i) {
                final o = options[i];
                return Center(
                  child: ChoiceChip(
                    label: Text(o.label),
                    selected: o.value == selected,
                    onSelected: (_) => onTap(o.value),
                    visualDensity: VisualDensity.compact,
                    labelStyle: theme.textTheme.labelMedium,
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _ExerciseTile extends StatelessWidget {
  const _ExerciseTile({required this.exercise});

  final Exercise exercise;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListTile(
      title: Text(exercise.name),
      subtitle: Text(
        [
          exercise.primaryMuscleLabel,
          exercise.equipmentLabel,
          if (exercise.unilateral) '单侧',
        ].join(' · '),
        style: theme.textTheme.bodySmall,
      ),
      trailing: exercise.hasGuide
          ? Icon(Icons.menu_book_outlined,
              size: 18, color: theme.colorScheme.outline)
          : null,
      onTap: () => _showDetail(context, exercise),
    );
  }
}

/// 动作详情。
///
/// 用底部面板而不是整页：内容就是「一段要领 + 一段常见错误」，
/// 整页跳转会让「返回」成为多余的一步。
void _showDetail(BuildContext context, Exercise e) {
  showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (_) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.6,
      maxChildSize: 0.9,
      builder: (_, controller) => ListView(
        controller: controller,
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          Text(e.name, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final t in [
                e.primaryMuscleLabel,
                e.equipmentLabel,
                e.metricTypeLabel,
                if (e.movementPatternLabel != null) e.movementPatternLabel!,
              ])
                Chip(
                  label: Text(t),
                  visualDensity: VisualDensity.compact,
                  labelStyle: Theme.of(context).textTheme.labelSmall,
                ),
            ],
          ),
          if (e.alias != null && e.alias!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text('别名：${e.alias}',
                style: Theme.of(context).textTheme.bodySmall),
          ],
          const SizedBox(height: 20),
          _Section(title: '动作要领', body: e.instructions),
          const SizedBox(height: 16),
          _Section(title: '常见错误', body: e.commonMistakes),
        ],
      ),
    ),
  );
}

/// 一节内容。**没内容时明确说「暂无」，不显示一个空标题**——
/// 空标题看起来像加载失败。
class _Section extends StatelessWidget {
  const _Section({required this.title, required this.body});
  final String title;
  final String? body;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final empty = body == null || body!.isEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: theme.textTheme.titleMedium),
        const SizedBox(height: 6),
        Text(
          empty ? '暂无' : body!,
          style: empty
              ? theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline)
              : theme.textTheme.bodyMedium?.copyWith(height: 1.6),
        ),
      ],
    );
  }
}
