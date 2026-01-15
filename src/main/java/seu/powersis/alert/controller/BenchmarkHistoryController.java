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
import seu.powersis.alert.service.ExaService;
import seu.powersis.alert.vo.BenchmarkHistoryVO;
import seu.powersis.alert.vo.ModelInfoVO;
import seu.powersis.alert.vo.PointVO;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/benchmark/history")
public class BenchmarkHistoryController {

    private final BenchmarkHistoryService benchmarkHistoryService;
    private final ModelViewService modelViewService;
    private final ExaService exaService;

    /**
     * 历史最优值趋势（正确逻辑）
     *
     * 逻辑说明：
     * 1. 获取模型配置（边界参数定义）
     * 2. 从 EXA 获取边界参数的历史数据
     * 3. 对于每个时间点：
     *    - 根据边界参数值计算 B_ID（网格坐标）
     *    - 在 benchmark_history 表中查找该 B_ID 对应的最优值
     * 4. 返回 (time, optimalValue) 列表
     */
    @GetMapping("")
    public Result<List<BenchmarkHistoryVO>> getHistory(BenchmarkHistoryQuery query) {

        // 0) 参数校验
        if (query == null || query.getModelId() == null) {
            log.warn("【历史最优值趋势】modelId 为空，query={}", query);
            return Result.success(Collections.emptyList());
        }

        log.info("【历史最优值趋势】开始查询，modelId={}, st={}, et={}",
                 query.getModelId(), query.getSt(), query.getEt());

        // 1) 获取模型配置
        ModelInfoVO modelInfoVO = null;
        String optimalType = "min"; // 默认寻优逻辑
        try {
            ModelView modelView = modelViewService.getById(query.getModelId());
            if (modelView != null && modelView.getModelInfo() != null) {
                modelInfoVO = JSON.parseObject(modelView.getModelInfo(), ModelInfoVO.class);
                if (modelInfoVO != null && modelInfoVO.getMovingWindows() != null
                        && modelInfoVO.getMovingWindows().getOptimalType() != null) {
                    optimalType = modelInfoVO.getMovingWindows().getOptimalType().trim().toLowerCase();
                }
            }
        } catch (Exception e) {
            log.error("【历史最优值趋势】获取模型配置失败", e);
            return Result.success(Collections.emptyList());
        }

        if (modelInfoVO == null) {
            log.warn("【历史最优值趋势】模型配置为空，modelId={}", query.getModelId());
            return Result.success(Collections.emptyList());
        }

        // 2) 获取边界参数配置
        List<PointVO> boundaryParams = modelInfoVO.getBoundaryParameter();
        if (boundaryParams == null || boundaryParams.isEmpty()) {
            log.warn("【历史最优值趋势】模型没有边界参数配置，modelId={}", query.getModelId());
            // 如果没有边界参数，回退到旧逻辑
            return Result.success(benchmarkHistoryService.getHistoryByType(query, optimalType));
        }

        // 3) 提取边界参数的测点号
        List<String> boundaryPointNames = boundaryParams.stream()
                .map(PointVO::getTargetPoint)
                .filter(p -> p != null && !p.trim().isEmpty())
                .collect(Collectors.toList());

        if (boundaryPointNames.isEmpty()) {
            log.warn("【历史最优值趋势】边界参数测点号为空，modelId={}", query.getModelId());
            return Result.success(benchmarkHistoryService.getHistoryByType(query, optimalType));
        }

        log.info("【历史最优值趋势】边界参数测点: {}", boundaryPointNames);

        // 4) 格式化时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String stStr = query.getSt() != null ? sdf.format(query.getSt()) : null;
        String etStr = query.getEt() != null ? sdf.format(query.getEt()) : null;

        // 5) 从 EXA 获取边界参数的历史数据
        Integer samplingInterval = 60; // 默认采样间隔 60 秒
        if (modelInfoVO.getMovingWindows() != null && modelInfoVO.getMovingWindows().getSamplingInterval() != null) {
            samplingInterval = modelInfoVO.getMovingWindows().getSamplingInterval();
        }

        List<Map<String, Object>> boundaryHistory;
        try {
            boundaryHistory = exaService.getMultiPointsHistory(boundaryPointNames, stStr, etStr, samplingInterval);
        } catch (Exception e) {
            log.error("【历史最优值趋势】获取边界参数历史数据失败", e);
            return Result.success(Collections.emptyList());
        }

        if (boundaryHistory == null || boundaryHistory.isEmpty()) {
            log.warn("【历史最优值趋势】边界参数历史数据为空");
            return Result.success(Collections.emptyList());
        }

        log.info("【历史最优值趋势】获取到 {} 个时间点的边界参数数据", boundaryHistory.size());

        // 6) 对于每个时间点，计算 B_ID 并查询最优值
        List<BenchmarkHistoryVO> result = new ArrayList<>();
        Map<String, Double> bidCache = new HashMap<>(); // 缓存 B_ID -> 最优值，避免重复查询

        for (Map<String, Object> row : boundaryHistory) {
            String timeStr = (String) row.get("time");
            @SuppressWarnings("unchecked")
            List<Double> values = (List<Double>) row.get("values");

            if (timeStr == null || values == null || values.size() != boundaryParams.size()) {
                continue;
            }

            // 计算 B_ID
            String bId = calculateBId(values, boundaryParams);
            if (bId == null) {
                continue;
            }

            // 查询该 B_ID 对应的最优值（使用缓存）
            Double optimalValue;
            if (bidCache.containsKey(bId)) {
                optimalValue = bidCache.get(bId);
            } else {
                optimalValue = benchmarkHistoryService.getOptimalValueByBId(query.getModelId(), bId, optimalType);
                bidCache.put(bId, optimalValue);
            }

            // 如果有最优值，添加到结果
            if (optimalValue != null) {
                try {
                    Date time = sdf.parse(timeStr);
                    result.add(new BenchmarkHistoryVO(time, optimalValue));
                } catch (Exception e) {
                    log.warn("【历史最优值趋势】解析时间失败: {}", timeStr);
                }
            }
        }

        log.info("【历史最优值趋势】查询完成，返回 {} 条数据，使用了 {} 个不同的 B_ID",
                 result.size(), bidCache.size());

        return Result.success(result);
    }

    /**
     * 根据边界参数值计算 B_ID
     *
     * B_ID 格式: B-{id1}-{id2}-...
     * 每个 id = 1 + floor((value - lowerlimit) / ((upperlimit - lowerlimit) / gridNumber))
     *
     * @param values 边界参数值列表
     * @param boundaryParams 边界参数配置列表
     * @return B_ID 字符串，如 "B-3-9"
     */
    private String calculateBId(List<Double> values, List<PointVO> boundaryParams) {
        if (values == null || boundaryParams == null || values.size() != boundaryParams.size()) {
            return null;
        }

        StringBuilder bId = new StringBuilder("B");

        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            PointVO param = boundaryParams.get(i);

            if (value == null || param == null) {
                return null;
            }

            BigDecimal upperLimit = param.getUpperlimit();
            BigDecimal lowerLimit = param.getLowerlimit();
            Integer gridNumber = param.getGridNumber();

            if (upperLimit == null || lowerLimit == null || gridNumber == null || gridNumber <= 0) {
                return null;
            }

            // 检查值是否在有效范围内
            double upper = upperLimit.doubleValue();
            double lower = lowerLimit.doubleValue();

            if (value < lower || value > upper) {
                // 值超出范围，跳过这个时间点
                return null;
            }

            // 计算网格 ID: 1 + floor((value - lower) / ((upper - lower) / gridNumber))
            double step = (upper - lower) / gridNumber;
            int gridId = 1 + (int) Math.floor((value - lower) / step);

            // 确保 gridId 在有效范围内
            if (gridId < 1) gridId = 1;
            if (gridId > gridNumber) gridId = gridNumber;

            bId.append("-").append(gridId);
        }

        return bId.toString();
    }
}
