/// 图表坐标轴的共享规则。
///
/// 放在 `core/` 而不是 `features/stats/`：4B 的体重 / 围度趋势图要用同一套规则，
/// 而这**不是**「统计页的排版细节」，是 fl_chart 的一个反直觉行为，
/// 抄第二遍就一定会有一份忘了修。
library;

import 'dart:math' as math;

/// 轴上该画标签的点下标集合。
///
/// ⚠️ **必须显式算出下标集合，不能只靠 `SideTitles.interval`。**
///
/// fl_chart 1.2 的 `AxisChartHelper.iterateThroughAxis` 结尾有这么一段
/// （`axis_chart_helper.dart:56`）：
///
/// ```dart
/// if (maxIncluded && !lastPositionOverlapsWithMax) {
///   yield max;      // ← 不管 interval 是多少，max 永远被额外补一个刻度
/// }
/// ```
///
/// 所以 `min=0, max=13, interval=3` 时它给出 `0/3/6/9/12` **再加一个 13**。
/// 最后两个标签只差一个数据单位，在手机上直接叠成一坨——
/// 真机上「趋势」页底部渲染成了 `9-79-14` 才发现的。
///
/// 越界检查（`i >= count`）挡不住它：13 是合法的下标，只是**离前一个太近**。
///
/// 所以这里自己算：**从最后一个点往前每隔 [step] 个取一个**。
/// 「从后往前」是有意的——**最后一个点（当前周 / 最近一次训练）是用户最关心的**，
/// 从 0 开始步进的话，14 个点的标签会落在 0/3/6/9/12，
/// 恰好把最该看的那个点漏掉。
///
/// 用法要配合 `SideTitles(interval: 1)`——见 [axisLabelStep] 的说明。
Set<int> axisLabelIndices(int count, {int target = 5}) {
  if (count <= 0) return const {};
  if (count <= target) return {for (var i = 0; i < count; i++) i};

  final step = (count / target).ceil();
  return {for (var i = count - 1; i >= 0; i -= step) i};
}

/// Y 轴范围 + 刻度间隔，对齐到「整齐」的数值。
typedef NiceRange = ({double min, double max, double step});

/// 把 Y 轴范围对齐到整齐的刻度上。
///
/// ⚠️ **又是 `iterateThroughAxis` 那个行为，这次在 Y 轴。**
///
/// 它不止补 `max`，`min` 也补（`axis_chart_helper.dart:48`）：
///
/// ```dart
/// if (minIncluded && !firstPositionOverlapsWithMin) {
///   yield min;
/// }
/// ```
///
/// 真机上踩到：体重图的 `minY = 73.9`（`lo - span*0.1` 算出来的），
/// 而刻度网格落在 74/76/78，于是 fl_chart 额外补了一个 73.9，
/// 和 74 **叠在一起**——左下角糊成一团。
///
/// 底轴可以用 [axisLabelIndices] 过滤下标，Y 轴不行——它是连续值，
/// 没有「第几个」可言。所以换一条路：**让 min/max 本身落在刻度网格上**，
/// 多余的补充刻度自然就不存在了。
///
/// 顺带解决另一个问题：不做对齐时，读数会长成 `120 / 100 / 80 / 60 / 50`
/// （最后两格间距只有前面的一半）。对齐之后是 `120 / 100 / 80 / 60 / 40`。
///
/// 算法就是标准的「nice number」：把原始步长归一到 1/2/5 × 10ⁿ。
///
/// @param lo 数据最小值
/// @param hi 数据最大值
/// @param targetTicks 期望的刻度格数，默认 4
NiceRange niceRange(double lo, double hi, {int targetTicks = 4}) {
  var span = hi - lo;
  // 所有值相等（或者只有一个点）时给一个最小跨度，
  // 否则下面会 log(0) 或除以 0
  if (span <= 0) {
    final mid = (hi + lo) / 2;
    lo = mid - 0.5;
    hi = mid + 0.5;
    span = 1;
  }

  final rawStep = span / targetTicks;
  // 10 的整数次幂，让 step 落在 [1,10) 的量级上
  final magnitude = math.pow(10, (math.log(rawStep) / math.ln10).floor()).toDouble();
  final normalized = rawStep / magnitude;
  final step = (normalized <= 1
          ? 1.0
          : normalized <= 2
              ? 2.0
              : normalized <= 5
                  ? 5.0
                  : 10.0) *
      magnitude;

  // ★ **上下各至少留一整格**。
  //
  // `METRICS 1.4` 要求「Y 轴范围取数据的最小/最大值各留 10% 余量」，
  // 目的是别让曲线贴着边框。但「对齐到网格」这一步会把余量整个吃掉——
  // 数据离网格只差一点点时，floor/ceil 之后余量可能只剩 0.1%，
  // 曲线看起来就是**从轴底冲到轴顶**，变化的幅度被夸大了。
  //
  // 这不是百分比问题，是**离散**问题：网格间距是 step，
  // 余量要么是 0 要么是 step 的整数倍，中间取不到。所以规则改成
  // 「不够一格就补一格」，得到的余量必然 ≥ 一个 step。
  //
  // 只补底部是不够的——最高点贴着顶边同样会让「涨到顶」的错觉留在图上。
  var min = (lo / step).floor() * step - step;
  var max = (hi / step).ceil() * step + step;

  return (min: min, max: max, step: step);
}

/// 给 `SideTitles.interval` 用。
///
/// ⚠️ **必须是 1，不能是「每隔几个」。**
///
/// 一旦把 `interval` 设成 3，fl_chart 就只把这些位置递给 `getTitlesWidget`，
/// 而它算出来的网格（`0/3/6/9/12`）和 [axisLabelIndices] 的网格
/// （`13/10/7/4/1`，从后往前）**起点不同、根本对不齐**——
/// 过滤之后一个标签都活不下来，轴变成空白。
///
/// 设成 1 就是「每个整数都问一遍」，由 [axisLabelIndices] 决定画不画。
/// 代价是 `getTitlesWidget` 多调用几十次、每次返回 `SizedBox.shrink()`，
/// 可以忽略。
const double axisTickInterval = 1;
