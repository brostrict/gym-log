import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:gym_log/core/chart_axis.dart';

/// `axisLabelIndices` 的测试。
///
/// 这个函数是为一个**真机上才暴露出来的** bug 写的：趋势页底部 X 轴
/// 把最后两个标签叠成了 `9-79-14`。根因在 fl_chart 的
/// `AxisChartHelper.iterateThroughAxis`（见 `chart_axis.dart` 的注释），
/// 它会无条件多补一个 `max` 刻度，越界检查挡不住。
///
/// 所以这里的断言分两类：
///   - **具体值**：钉死「哪些下标有标签」，防止有人改回 `interval: n`
///   - **不变量**：不管几个点，标签都不会挤在一起、且最后一个点一定有标签
void main() {
  group('axisLabelIndices', () {
    test('没有数据时返回空集', () {
      expect(axisLabelIndices(0), isEmpty);
    });

    test('点数不超过 target 时全部画出来', () {
      expect(axisLabelIndices(1), {0});
      expect(axisLabelIndices(3), {0, 1, 2});
      expect(axisLabelIndices(5), {0, 1, 2, 3, 4});
    });

    test('14 个点（3 个月）隔 3 个取一个，从最后一个往前', () {
      // 就是真机上出问题的那个尺寸。
      // 注意 12 不在集合里 —— 它和 13 只差一个数据单位，正是叠在一起的那两个。
      expect(axisLabelIndices(14), {13, 10, 7, 4, 1});
      expect(axisLabelIndices(14), isNot(contains(12)));
    });

    test('26 个点（6 个月）', () {
      expect(axisLabelIndices(26), {25, 19, 13, 7, 1});
    });

    test('53 个点（1 年）', () {
      expect(axisLabelIndices(53), {52, 41, 30, 19, 8});
    });

    test('6 个点刚好超过 target，步长取 2 而不是 1', () {
      expect(axisLabelIndices(6), {5, 3, 1});
    });
  });

  group('不变量', () {
    // 覆盖各种尺寸，包括两端和几个质数（步长算不尽的情况）
    const sizes = [0, 1, 2, 5, 6, 7, 9, 14, 26, 53, 104, 365];

    test('最后一个点永远有标签', () {
      // 这是「从后往前」的唯一理由：最后那个点是当前周 / 最近一次训练，
      // 是用户最关心的。从 0 开始步进的话 14 个点会落在 0/3/6/9/12，
      // 恰好把最该看的那个漏掉。
      for (final n in sizes.where((n) => n > 0)) {
        expect(
          axisLabelIndices(n),
          contains(n - 1),
          reason: '$n 个点的标签里没有最后一个点',
        );
      }
    });

    test('下标不越界', () {
      for (final n in sizes) {
        expect(
          axisLabelIndices(n).every((i) => i >= 0 && i < n),
          isTrue,
          reason: '$n 个点算出了越界下标',
        );
      }
    });

    test('点够多时相邻标签至少隔 2 个数据单位', () {
      // 隔 1 个就是原 bug 的形态：两个标签挤在一起看不清。
      for (final n in sizes.where((n) => n > 5)) {
        final sorted = axisLabelIndices(n).toList()..sort();
        for (var k = 1; k < sorted.length; k++) {
          expect(
            sorted[k] - sorted[k - 1],
            greaterThanOrEqualTo(2),
            reason: '$n 个点：下标 ${sorted[k - 1]} 和 ${sorted[k]} 挨得太近',
          );
        }
      }
    });

    test('标签数量不会失控', () {
      // 横屏 / 大屏也最多这么几个，不至于糊成一片
      for (final n in sizes) {
        expect(axisLabelIndices(n).length, lessThanOrEqualTo(6),
            reason: '$n 个点画了太多标签');
      }
    });
  });

  group('niceRange', () {
    test('范围对齐到整齐刻度，min/max 都落在格子上', () {
      // 就是真机上出问题的那个：体重 72.9–79.4
      final r = niceRange(72.9, 79.4);

      // 对齐之后 min/max 必然是 step 的整数倍——
      // 这正是「fl_chart 不会再多补一个刻度」的原因
      expect(r.min % r.step, closeTo(0, 1e-9));
      expect(r.max % r.step, closeTo(0, 1e-9));
      expect(r.min, lessThan(72.9));
      expect(r.max, greaterThan(79.4));
    });

    test('数据占图高的比例不至于被压得太扁', () {
      // 补格是有限度的：数据本身至少要占到 40%，
      // 否则曲线会缩成中间一条平线，反而看不清趋势
      for (final pair in [(72.9, 79.4), (70.0, 80.0), (18.7, 22.0), (99.0, 101.0)]) {
        final r = niceRange(pair.$1, pair.$2);
        final ratio = (pair.$2 - pair.$1) / (r.max - r.min);
        expect(ratio, greaterThan(0.4),
            reason: '${pair.$1}–${pair.$2} 只占图高的 ${(ratio * 100).round()}%');
      }
    });

    test('★ 上下各至少留一整格 —— 否则曲线看起来「从最低冲到最高」', () {
      // 这是用户提的：只对齐到网格的话，数据离网格近时余量几乎为 0，
      // 曲线顶天立地，变化的幅度被夸大。
      for (final pair in [(70.0, 80.0), (72.9, 79.4), (99.0, 101.0), (18.7, 22.0)]) {
        final r = niceRange(pair.$1, pair.$2);
        expect(r.min, lessThanOrEqualTo(pair.$1 - r.step),
            reason: '${pair.$1} 下方不足一格');
        expect(r.max, greaterThanOrEqualTo(pair.$2 + r.step),
            reason: '${pair.$2} 上方不足一格');
      }
    });

    test('步长只取 1/2/5 × 10ⁿ', () {
      for (final pair in [
        (72.9, 79.4),
        (0.0, 1.0),
        (100.0, 5000.0),
        (-5.0, 5.0),
        (0.001, 0.009),
      ]) {
        final r = niceRange(pair.$1, pair.$2);
        final normalized = r.step / math.pow(10, (math.log(r.step) / math.ln10).floor());
        expect(normalized, anyOf(closeTo(1, 1e-9), closeTo(2, 1e-9), closeTo(5, 1e-9)),
            reason: '${pair.$1}–${pair.$2} 的步长 ${r.step} 不是 1/2/5 的量级');
      }
    });

    test('★ 所有值相等时不崩、不产生 0 跨度', () {
      // 只有一个数据点，或者所有值一样——span = 0，
      // 不做保护的话下面会 log(0) 或除以 0
      final r = niceRange(73.3, 73.3);
      expect(r.max, greaterThan(r.min));
      expect(r.step, greaterThan(0));
      expect(r.min.isFinite, isTrue);
      expect(r.max.isFinite, isTrue);
    });

    test('负数范围也正确（温度、相对变化这类指标）', () {
      final r = niceRange(-3.2, 4.7);
      expect(r.min, lessThanOrEqualTo(-3.2));
      expect(r.max, greaterThanOrEqualTo(4.7));
      expect(r.min % r.step, closeTo(0, 1e-9));
    });

    test('极小的范围（体脂率变化 0.1%）也能给出非零步长', () {
      final r = niceRange(18.2, 18.3);
      expect(r.step, greaterThan(0));
      expect(r.max - r.min, greaterThan(0));
      expect(r.step.isFinite, isTrue);
    });

    test('刻度格数不会失控', () {
      for (final pair in [(72.9, 79.4), (0.0, 1.0), (100.0, 5000.0), (-5.0, 5.0)]) {
        final r = niceRange(pair.$1, pair.$2);
        final ticks = ((r.max - r.min) / r.step).round();
        // 对齐 + 上下各补一格，最多 targetTicks + 3
        // （补格会让总跨度变大，但 step 是按**原始 span** 算的，不会被推着继续变大）
        expect(ticks, lessThanOrEqualTo(7),
            reason: '${pair.$1}–${pair.$2} 画了 $ticks 格');
      }
    });
  });

  test('axisTickInterval 必须是 1', () {
    // 不是 1 的话 fl_chart 只把网格点递进来，而它的网格（从 min 起）
    // 和 axisLabelIndices 的网格（从 max 起）对不齐 → 过滤后一个标签都不剩，
    // 轴直接变空白。这条断言是给「顺手把它调大一点省点调用」的人准备的。
    expect(axisTickInterval, 1);
  });
}
