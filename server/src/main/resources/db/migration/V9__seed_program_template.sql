-- ============================================================
-- V9 —— 6 个内置计划模板
--
-- 【重要】关于训练日的循环语义
--
-- 训练日在**整个计划周期内连续循环**，不是每周重置。
-- 这样 5×5 的 A/B 交替才能正确表达：
--
--   2 个训练日模板（A、B）+ 每周 3 练
--     第 1 周：第1次→A  第2次→B  第3次→A
--     第 2 周：第4次→B  第5次→A  第6次→B      ← 自动交替了
--
-- 如果用「每周重置」，第 2 周又会从 A 开始，变成 A/B/A 无限重复。
--
-- PPL（3 个模板 × 3 练）和上下肢（4 × 4）正好整除，循环效果相同。
--
-- 这个规则在步骤 2.15「今天练什么」里实现。
--
-- 【关于动作引用】structure 里用**动作名称**而不是 id——
-- 动作 id 是自增的，各环境不一致。运行时按名称查 id。
-- ============================================================


-- ------------------------------------------------------------
-- 1. 5×5 力量（StrongLifts 风格）
-- ------------------------------------------------------------
INSERT INTO `program_template`
(`code`, `name`, `description`, `goal`, `level`, `sessions_per_week`, `total_weeks`,
 `days_per_week`, `equipment_summary`, `estimated_minutes`, `structure`, `sort_order`, `status`)
VALUES ('STRONGLIFTS_5X5',
        '5×5 力量入门（每周3练）',
        '最经典的入门力量计划。每次训练只做 3 个复合动作，每个动作 5 组 5 次，A/B 两天交替。每周在上一周基础上加重，进步直观。适合完全新手建立基础力量。',
        'STRENGTH', 'BEGINNER', 3, 12, 2,
        '杠铃、深蹲架、卧推凳、举重台', 45,
        '{
          "weeks": [
            {"weekNumber":1,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":0,"isDeload":false,"note":"建立动作模式，重量以能标准完成为准"},
            {"weekNumber":2,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false,"note":"开始加重"},
            {"weekNumber":3,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":4,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":5,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":6,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":7,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":8,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":9,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":10,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":11,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false,"note":"最后一周冲重量"},
            {"weekNumber":12,"sessionsPerWeek":2,"weightAdjustPct":-40,"setAdjust":-2,"isDeload":true,"note":"减量周，让身体恢复"}
          ],
          "days": [
            {"dayNumber":1,"name":"A 日","isRestDay":false,"exercises":[
              {"exerciseName":"杠铃深蹲","orderIndex":1,"targetSets":5,"targetRepsMin":5,"targetRepsMax":5,"restSec":180,"targetWeightType":"ABSOLUTE","note":"核心动作，重量优先"},
              {"exerciseName":"杠铃卧推","orderIndex":2,"targetSets":5,"targetRepsMin":5,"targetRepsMax":5,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"杠铃划船","orderIndex":3,"targetSets":5,"targetRepsMin":5,"targetRepsMax":5,"restSec":180,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":2,"name":"B 日","isRestDay":false,"exercises":[
              {"exerciseName":"杠铃深蹲","orderIndex":1,"targetSets":5,"targetRepsMin":5,"targetRepsMax":5,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"站姿杠铃推举","orderIndex":2,"targetSets":5,"targetRepsMin":5,"targetRepsMax":5,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"硬拉","orderIndex":3,"targetSets":1,"targetRepsMin":5,"targetRepsMax":5,"restSec":180,"targetWeightType":"ABSOLUTE","note":"只做 1 组，硬拉恢复成本高"}
            ]}
          ]
        }',
        10, 1);


-- ------------------------------------------------------------
-- 2. 推拉腿三分化（每周3练）
-- ------------------------------------------------------------
INSERT INTO `program_template`
(`code`, `name`, `description`, `goal`, `level`, `sessions_per_week`, `total_weeks`,
 `days_per_week`, `equipment_summary`, `estimated_minutes`, `structure`, `sort_order`, `status`)
VALUES ('PPL_3DAY',
        '推拉腿三分化（每周3练）',
        '按动作模式分化的经典方案：推日练胸肩三头，拉日练背二头，腿日练下肢。每个肌群每周刺激一次，适合时间有限但想全面发展的训练者。',
        'MUSCLE_GAIN', 'INTERMEDIATE', 3, 8, 3,
        '杠铃、哑铃、龙门架、腿举机', 60,
        '{
          "weeks": [
            {"weekNumber":1,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":0,"isDeload":false,"note":"适应期，重量留有余力"},
            {"weekNumber":2,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":3,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":4,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":1,"isDeload":false,"note":"加一组，提高训练量"},
            {"weekNumber":5,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":1,"isDeload":false},
            {"weekNumber":6,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":1,"isDeload":false},
            {"weekNumber":7,"sessionsPerWeek":3,"weightAdjustPct":2.5,"setAdjust":1,"isDeload":false,"note":"冲强度"},
            {"weekNumber":8,"sessionsPerWeek":2,"weightAdjustPct":-40,"setAdjust":-2,"isDeload":true,"note":"减量周"}
          ],
          "days": [
            {"dayNumber":1,"name":"推日（胸肩三头）","isRestDay":false,"exercises":[
              {"exerciseName":"杠铃卧推","orderIndex":1,"targetSets":4,"targetRepsMin":6,"targetRepsMax":8,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"上斜哑铃卧推","orderIndex":2,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"坐姿哑铃推举","orderIndex":3,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"哑铃侧平举","orderIndex":4,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"绳索下压","orderIndex":5,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":2,"name":"拉日（背二头）","isRestDay":false,"exercises":[
              {"exerciseName":"引体向上","orderIndex":1,"targetSets":4,"targetRepsMin":6,"targetRepsMax":10,"restSec":150,"targetWeightType":"ABSOLUTE","note":"做不动可用高位下拉替代"},
              {"exerciseName":"杠铃划船","orderIndex":2,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"坐姿绳索划船","orderIndex":3,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"面拉","orderIndex":4,"targetSets":3,"targetRepsMin":15,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE","note":"肩后束与肩袖，别省"},
              {"exerciseName":"杠铃弯举","orderIndex":5,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":3,"name":"腿日","isRestDay":false,"exercises":[
              {"exerciseName":"杠铃深蹲","orderIndex":1,"targetSets":4,"targetRepsMin":6,"targetRepsMax":8,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"罗马尼亚硬拉","orderIndex":2,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"腿举","orderIndex":3,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"腿弯举","orderIndex":4,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"站姿提踵","orderIndex":5,"targetSets":4,"targetRepsMin":15,"targetRepsMax":20,"restSec":45,"targetWeightType":"ABSOLUTE"}
            ]}
          ]
        }',
        20, 1);


-- ------------------------------------------------------------
-- 3. 上下肢分化（每周4练）
-- ------------------------------------------------------------
INSERT INTO `program_template`
(`code`, `name`, `description`, `goal`, `level`, `sessions_per_week`, `total_weeks`,
 `days_per_week`, `equipment_summary`, `estimated_minutes`, `structure`, `sort_order`, `status`)
VALUES ('UPPER_LOWER_4DAY',
        '上下肢分化（每周4练）',
        '上肢与下肢交替训练，每个肌群每周刺激两次。频率高于三分化，适合恢复能力较好、想把训练量做上去的人。',
        'MUSCLE_GAIN', 'INTERMEDIATE', 4, 8, 4,
        '杠铃、哑铃、龙门架、腿举机', 55,
        '{
          "weeks": [
            {"weekNumber":1,"sessionsPerWeek":4,"weightAdjustPct":0,"setAdjust":0,"isDeload":false,"note":"适应期"},
            {"weekNumber":2,"sessionsPerWeek":4,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":3,"sessionsPerWeek":4,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":4,"sessionsPerWeek":4,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":5,"sessionsPerWeek":4,"weightAdjustPct":5,"setAdjust":0,"isDeload":false,"note":"强度上台阶"},
            {"weekNumber":6,"sessionsPerWeek":4,"weightAdjustPct":5,"setAdjust":0,"isDeload":false},
            {"weekNumber":7,"sessionsPerWeek":4,"weightAdjustPct":5,"setAdjust":0,"isDeload":false},
            {"weekNumber":8,"sessionsPerWeek":2,"weightAdjustPct":-40,"setAdjust":-1,"isDeload":true,"note":"减量周"}
          ],
          "days": [
            {"dayNumber":1,"name":"上肢 A（推为主）","isRestDay":false,"exercises":[
              {"exerciseName":"杠铃卧推","orderIndex":1,"targetSets":4,"targetRepsMin":6,"targetRepsMax":8,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"杠铃划船","orderIndex":2,"targetSets":4,"targetRepsMin":6,"targetRepsMax":8,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"坐姿哑铃推举","orderIndex":3,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"高位下拉","orderIndex":4,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"绳索下压","orderIndex":5,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"杠铃弯举","orderIndex":6,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":2,"name":"下肢 A（深蹲为主）","isRestDay":false,"exercises":[
              {"exerciseName":"杠铃深蹲","orderIndex":1,"targetSets":4,"targetRepsMin":6,"targetRepsMax":8,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"罗马尼亚硬拉","orderIndex":2,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"腿举","orderIndex":3,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"腿屈伸","orderIndex":4,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"站姿提踵","orderIndex":5,"targetSets":4,"targetRepsMin":15,"targetRepsMax":20,"restSec":45,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":3,"name":"上肢 B（拉为主）","isRestDay":false,"exercises":[
              {"exerciseName":"引体向上","orderIndex":1,"targetSets":4,"targetRepsMin":6,"targetRepsMax":10,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"上斜哑铃卧推","orderIndex":2,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"坐姿绳索划船","orderIndex":3,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"哑铃侧平举","orderIndex":4,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"仰卧臂屈伸","orderIndex":5,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"锤式弯举","orderIndex":6,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":4,"name":"下肢 B（硬拉为主）","isRestDay":false,"exercises":[
              {"exerciseName":"硬拉","orderIndex":1,"targetSets":3,"targetRepsMin":5,"targetRepsMax":5,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"保加利亚分腿蹲","orderIndex":2,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"臀冲","orderIndex":3,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"腿弯举","orderIndex":4,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"坐姿提踵","orderIndex":5,"targetSets":4,"targetRepsMin":15,"targetRepsMax":20,"restSec":45,"targetWeightType":"ABSOLUTE"}
            ]}
          ]
        }',
        30, 1);


-- ------------------------------------------------------------
-- 4. 徒手全身（每周3练）
--
-- 零器械。出差、居家、健身房器械被占时都能用。
-- 全部动作用 REPS_ONLY 类型，容量按 体重 × bw_factor × 次数 计算。
-- ------------------------------------------------------------
INSERT INTO `program_template`
(`code`, `name`, `description`, `goal`, `level`, `sessions_per_week`, `total_weeks`,
 `days_per_week`, `equipment_summary`, `estimated_minutes`, `structure`, `sort_order`, `status`)
VALUES ('BODYWEIGHT_3DAY',
        '徒手全身训练（每周3练 · 零器械）',
        '完全不需要器械。三个训练日分别侧重推、拉、腿，用自重动作覆盖全身。出差住酒店、居家、健身房器械被占时都能照常训练——计划不会因为环境中断。',
        'GENERAL', 'BEGINNER', 3, 8, 3,
        '零器械（可选：单杠、椅子）', 35,
        '{
          "weeks": [
            {"weekNumber":1,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":0,"isDeload":false,"note":"先保证动作标准，次数以留 2 次余力为准"},
            {"weekNumber":2,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":1,"isDeload":false,"note":"加一组"},
            {"weekNumber":3,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":1,"isDeload":false},
            {"weekNumber":4,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":1,"isDeload":false},
            {"weekNumber":5,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":2,"isDeload":false,"note":"次数做上去了再加组"},
            {"weekNumber":6,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":2,"isDeload":false},
            {"weekNumber":7,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":2,"isDeload":false},
            {"weekNumber":8,"sessionsPerWeek":2,"weightAdjustPct":0,"setAdjust":-1,"isDeload":true,"note":"减量周"}
          ],
          "days": [
            {"dayNumber":1,"name":"全身 A（推为主）","isRestDay":false,"exercises":[
              {"exerciseName":"俯卧撑","orderIndex":1,"targetSets":4,"targetRepsMin":8,"targetRepsMax":15,"restSec":90,"targetWeightType":"ABSOLUTE","note":"做不动可改上斜俯卧撑"},
              {"exerciseName":"派克俯卧撑","orderIndex":2,"targetSets":3,"targetRepsMin":6,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"箭步蹲","orderIndex":3,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"臀桥","orderIndex":4,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"平板支撑","orderIndex":5,"targetSets":3,"targetRepsMin":30,"targetRepsMax":60,"restSec":60,"targetWeightType":"ABSOLUTE","note":"目标是秒数"}
            ]},
            {"dayNumber":2,"name":"全身 B（拉为主）","isRestDay":false,"exercises":[
              {"exerciseName":"反向划船","orderIndex":1,"targetSets":4,"targetRepsMin":8,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE","note":"找一张稳固的桌子或低杠"},
              {"exerciseName":"引体向上","orderIndex":2,"targetSets":3,"targetRepsMin":1,"targetRepsMax":8,"restSec":120,"targetWeightType":"ABSOLUTE","note":"做不了可先做悬垂或离心"},
              {"exerciseName":"单腿臀桥","orderIndex":3,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"登山者","orderIndex":4,"targetSets":3,"targetRepsMin":20,"targetRepsMax":30,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"死虫","orderIndex":5,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":45,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":3,"name":"全身 C（腿为主）","isRestDay":false,"exercises":[
              {"exerciseName":"深蹲跳","orderIndex":1,"targetSets":4,"targetRepsMin":10,"targetRepsMax":15,"restSec":90,"targetWeightType":"ABSOLUTE","note":"落地要轻，膝盖不适就换成箱子深蹲"},
              {"exerciseName":"箱子深蹲","orderIndex":2,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"单腿臀桥","orderIndex":3,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"凳上反屈伸","orderIndex":4,"targetSets":3,"targetRepsMin":10,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"卷腹","orderIndex":5,"targetSets":3,"targetRepsMin":15,"targetRepsMax":20,"restSec":45,"targetWeightType":"ABSOLUTE"}
            ]}
          ]
        }',
        40, 1);


-- ------------------------------------------------------------
-- 5. 徒手入门（零基础）
-- ------------------------------------------------------------
INSERT INTO `program_template`
(`code`, `name`, `description`, `goal`, `level`, `sessions_per_week`, `total_weeks`,
 `days_per_week`, `equipment_summary`, `estimated_minutes`, `structure`, `sort_order`, `status`)
VALUES ('BODYWEIGHT_BEGINNER',
        '徒手入门（零基础 · 每周3练）',
        '完全没练过的人的起点。两个训练日交替，动作简单、组数少，重点是养成习惯和建立基础动作模式。练完不酸痛得下不了楼，才能坚持下去。',
        'GENERAL', 'BEGINNER', 3, 4, 2,
        '零器械', 25,
        '{
          "weeks": [
            {"weekNumber":1,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":0,"isDeload":false,"note":"第一周只求完成，不求强度"},
            {"weekNumber":2,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":1,"isDeload":false,"note":"加一组"},
            {"weekNumber":3,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":1,"isDeload":false},
            {"weekNumber":4,"sessionsPerWeek":3,"weightAdjustPct":0,"setAdjust":1,"isDeload":false,"note":"四周结束可转入徒手全身计划"}
          ],
          "days": [
            {"dayNumber":1,"name":"入门 A","isRestDay":false,"exercises":[
              {"exerciseName":"上斜俯卧撑","orderIndex":1,"targetSets":3,"targetRepsMin":8,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE","note":"手撑高一点，比标准俯卧撑容易"},
              {"exerciseName":"箱子深蹲","orderIndex":2,"targetSets":3,"targetRepsMin":10,"targetRepsMax":15,"restSec":90,"targetWeightType":"ABSOLUTE","note":"坐到椅子上再站起来"},
              {"exerciseName":"臀桥","orderIndex":3,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"平板支撑","orderIndex":4,"targetSets":3,"targetRepsMin":20,"targetRepsMax":40,"restSec":60,"targetWeightType":"ABSOLUTE","note":"目标是秒数"}
            ]},
            {"dayNumber":2,"name":"入门 B","isRestDay":false,"exercises":[
              {"exerciseName":"反向划船","orderIndex":1,"targetSets":3,"targetRepsMin":6,"targetRepsMax":10,"restSec":90,"targetWeightType":"ABSOLUTE","note":"身体越直立越容易"},
              {"exerciseName":"箭步蹲","orderIndex":2,"targetSets":2,"targetRepsMin":8,"targetRepsMax":10,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"单腿臀桥","orderIndex":3,"targetSets":3,"targetRepsMin":8,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"卷腹","orderIndex":4,"targetSets":3,"targetRepsMin":10,"targetRepsMax":15,"restSec":45,"targetWeightType":"ABSOLUTE"}
            ]}
          ]
        }',
        50, 1);


-- ------------------------------------------------------------
-- 6. 推拉腿六分化（每周6练 · 进阶）
-- ------------------------------------------------------------
INSERT INTO `program_template`
(`code`, `name`, `description`, `goal`, `level`, `sessions_per_week`, `total_weeks`,
 `days_per_week`, `equipment_summary`, `estimated_minutes`, `structure`, `sort_order`, `status`)
VALUES ('PPL_6DAY',
        '推拉腿六分化（每周6练 · 进阶）',
        '把推拉腿各拆成两次，每个肌群每周刺激两次。训练量大、恢复要求高，适合有一定基础且时间充裕的人。恢复不过来就退回每周 3 练。',
        'MUSCLE_GAIN', 'ADVANCED', 6, 8, 6,
        '杠铃、哑铃、龙门架、腿举机', 50,
        '{
          "weeks": [
            {"weekNumber":1,"sessionsPerWeek":6,"weightAdjustPct":0,"setAdjust":0,"isDeload":false,"note":"先把频率适应下来，重量保守"},
            {"weekNumber":2,"sessionsPerWeek":6,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":3,"sessionsPerWeek":6,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":4,"sessionsPerWeek":6,"weightAdjustPct":2.5,"setAdjust":0,"isDeload":false},
            {"weekNumber":5,"sessionsPerWeek":6,"weightAdjustPct":2.5,"setAdjust":1,"isDeload":false,"note":"加量"},
            {"weekNumber":6,"sessionsPerWeek":6,"weightAdjustPct":2.5,"setAdjust":1,"isDeload":false},
            {"weekNumber":7,"sessionsPerWeek":6,"weightAdjustPct":2.5,"setAdjust":1,"isDeload":false},
            {"weekNumber":8,"sessionsPerWeek":3,"weightAdjustPct":-40,"setAdjust":-2,"isDeload":true,"note":"减量周，六练的人尤其需要"}
          ],
          "days": [
            {"dayNumber":1,"name":"推 A（力量）","isRestDay":false,"exercises":[
              {"exerciseName":"杠铃卧推","orderIndex":1,"targetSets":4,"targetRepsMin":5,"targetRepsMax":6,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"站姿杠铃推举","orderIndex":2,"targetSets":3,"targetRepsMin":6,"targetRepsMax":8,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"窄距卧推","orderIndex":3,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"哑铃侧平举","orderIndex":4,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":2,"name":"拉 A（力量）","isRestDay":false,"exercises":[
              {"exerciseName":"硬拉","orderIndex":1,"targetSets":3,"targetRepsMin":4,"targetRepsMax":6,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"引体向上","orderIndex":2,"targetSets":4,"targetRepsMin":6,"targetRepsMax":10,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"杠铃划船","orderIndex":3,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"杠铃弯举","orderIndex":4,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":60,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":3,"name":"腿 A（深蹲为主）","isRestDay":false,"exercises":[
              {"exerciseName":"杠铃深蹲","orderIndex":1,"targetSets":4,"targetRepsMin":6,"targetRepsMax":8,"restSec":180,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"罗马尼亚硬拉","orderIndex":2,"targetSets":3,"targetRepsMin":8,"targetRepsMax":10,"restSec":150,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"腿屈伸","orderIndex":3,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"站姿提踵","orderIndex":4,"targetSets":4,"targetRepsMin":15,"targetRepsMax":20,"restSec":45,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":4,"name":"推 B（增肌）","isRestDay":false,"exercises":[
              {"exerciseName":"上斜哑铃卧推","orderIndex":1,"targetSets":4,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"器械推胸","orderIndex":2,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"哑铃侧平举","orderIndex":3,"targetSets":4,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"绳索下压","orderIndex":4,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":5,"name":"拉 B（增肌）","isRestDay":false,"exercises":[
              {"exerciseName":"高位下拉","orderIndex":1,"targetSets":4,"targetRepsMin":10,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"坐姿绳索划船","orderIndex":2,"targetSets":4,"targetRepsMin":10,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"哑铃单臂划船","orderIndex":3,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":90,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"面拉","orderIndex":4,"targetSets":3,"targetRepsMin":15,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"锤式弯举","orderIndex":5,"targetSets":3,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"}
            ]},
            {"dayNumber":6,"name":"腿 B（硬拉为主）","isRestDay":false,"exercises":[
              {"exerciseName":"保加利亚分腿蹲","orderIndex":1,"targetSets":4,"targetRepsMin":8,"targetRepsMax":10,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"臀冲","orderIndex":2,"targetSets":3,"targetRepsMin":10,"targetRepsMax":12,"restSec":120,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"腿弯举","orderIndex":3,"targetSets":4,"targetRepsMin":12,"targetRepsMax":15,"restSec":60,"targetWeightType":"ABSOLUTE"},
              {"exerciseName":"坐姿提踵","orderIndex":4,"targetSets":4,"targetRepsMin":15,"targetRepsMax":20,"restSec":45,"targetWeightType":"ABSOLUTE"}
            ]}
          ]
        }',
        60, 1);


-- ============================================================
-- 验证
-- ============================================================
SELECT `code`                                  AS `编码`,
       `name`                                  AS `名称`,
       `goal`                                  AS `目标`,
       `level`                                 AS `水平`,
       `sessions_per_week`                     AS `每周次数`,
       JSON_LENGTH(`structure` -> '$.days')    AS `训练日数`,
       JSON_LENGTH(`structure` -> '$.weeks')   AS `周数`
FROM `program_template`
ORDER BY `sort_order`;
