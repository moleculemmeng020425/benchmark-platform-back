package seu.powersis.alert.controller;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import seu.powersis.alert.common.model.Result;
import seu.powersis.alert.dao.entity.ModelView;
import seu.powersis.alert.dao.service.BenchmarkHistoryService;
import seu.powersis.alert.dao.service.ModelViewService;
import seu.powersis.alert.param.BenchmarkHistoryQuery;
import seu.powersis.alert.vo.BenchmarkHistoryVO;
import seu.powersis.alert.vo.ModelInfoVO;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/benchmark/history")
public class BenchmarkHistoryController {

    private final BenchmarkHistoryService benchmarkHistoryService;
    private final ModelViewService modelViewService;

    /**
     * 历史最优值趋势（来自本地 SQL：benchmark_history 表）
     *
     * query: modelId 必填；st/et 选填
     * 返回: List<BenchmarkHistoryVO>(time=starttime, value=targetvalue)
     * 
     * 修复：根据模型的 marktype 筛选对应类型的数据
     * - marktype='min' → 查询 type='min' 的数据（目标值越低越好）
     * - marktype='max' → 查询 type='max' 的数据（目标值越高越好）
     */
    @GetMapping("")
    public Result<List<BenchmarkHistoryVO>> getHistory(BenchmarkHistoryQuery query) {

        // 0) 参数校验
        if (query == null || query.getModelId() == null) {
            log.warn("【历史最优值趋势】modelId 为空，query={}", query);
            return Result.success(Collections.emptyList());
        }

        // 1) 获取模型的寻优逻辑类型（优化方向）
        String marktype = "min"; // 默认目标值越低越好
        try {
            ModelView modelView = modelViewService.getById(query.getModelId());
            if (modelView != null && modelView.getModelInfo() != null) {
                ModelInfoVO modelInfoVO = JSON.parseObject(modelView.getModelInfo(), ModelInfoVO.class);
                if (modelInfoVO != null) {
                    // 优先从 movingWindows.optimalType 读取（新字段）
                    if (modelInfoVO.getMovingWindows() != null
                        && modelInfoVO.getMovingWindows().getOptimalType() != null
                        && !modelInfoVO.getMovingWindows().getOptimalType().trim().isEmpty()) {
                        marktype = modelInfoVO.getMovingWindows().getOptimalType().trim().toLowerCase();
                    }
                    // 兼容旧数据：如果 optimalType 没有，尝试从 targetParameter.marktype 读取
                    else if (modelInfoVO.getTargetParameter() != null
                        && modelInfoVO.getTargetParameter().getMarktype() != null
                        && !modelInfoVO.getTargetParameter().getMarktype().trim().isEmpty()) {
                        marktype = modelInfoVO.getTargetParameter().getMarktype().trim().toLowerCase();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("【历史最优值趋势】获取模型寻优逻辑失败，使用默认值 min", e);
        }

        // 2) 走 SQL（benchmark_history），只查询对应 type 的数据
        List<BenchmarkHistoryVO> out;
        try {
            out = benchmarkHistoryService.getHistoryByType(query, marktype);
        } catch (Exception e) {
            log.error("【历史最优值趋势】SQL 查询失败，query={}, marktype={}", query, marktype, e);
            return Result.success(Collections.emptyList());
        }

        // 3) 返回（允许空列表）
        return Result.success(out);
    }
}
