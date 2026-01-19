package seu.powersis.alert.controller;

import cn.hutool.core.util.StrUtil;
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

import java.text.SimpleDateFormat;
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
     * 历史最优值趋势（动态调用算法服务计算，不从SQL读取）
     *
     * query: modelId 必填；st/et 选填；boundaryValues 边界参数值（逗号分隔）
     * 返回: List<BenchmarkHistoryVO>(time, value)
     *
     * 每输入一组边界参数、模型ID、st、et就调用算法接口计算一次
     */
    @GetMapping("")
    public Result<List<BenchmarkHistoryVO>> getHistory(BenchmarkHistoryQuery query) {

        // 0) 参数校验
        if (query == null || query.getModelId() == null) {
            log.warn("【历史最优值趋势】modelId 为空，query={}", query);
            return Result.success(Collections.emptyList());
        }

        // 1) 获取模型信息
        ModelView modelView;
        ModelInfoVO modelInfoVO;
        String marktype = "min"; // 默认目标值越低越好
        try {
            modelView = modelViewService.getById(query.getModelId());
            if (modelView == null || modelView.getModelInfo() == null) {
                log.warn("【历史最优值趋势】模型不存在或模型信息为空，modelId={}", query.getModelId());
                return Result.success(Collections.emptyList());
            }
            modelInfoVO = JSON.parseObject(modelView.getModelInfo(), ModelInfoVO.class);
            if (modelInfoVO == null) {
                log.warn("【历史最优值趋势】解析模型信息失败，modelId={}", query.getModelId());
                return Result.success(Collections.emptyList());
            }
            if (modelInfoVO.getTargetParameter() != null
                && modelInfoVO.getTargetParameter().getMarktype() != null) {
                marktype = modelInfoVO.getTargetParameter().getMarktype().trim().toLowerCase();
            }
        } catch (Exception e) {
            log.error("【历史最优值趋势】获取模型信息失败，modelId={}", query.getModelId(), e);
            return Result.success(Collections.emptyList());
        }

        // 2) 解析边界参数值
        Float[] boundaryValues = parseBoundaryValues(query.getBoundaryValues());
        if (boundaryValues == null || boundaryValues.length == 0) {
            log.warn("【历史最优值趋势】边界参数为空，使用默认值");
            // 如果没有传入边界参数，使用默认空数组
            boundaryValues = new Float[0];
        }

        // 3) 格式化时间参数
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String st = query.getSt() != null ? dateFormat.format(query.getSt()) : null;
        String et = query.getEt() != null ? dateFormat.format(query.getEt()) : null;

        // 4) 调用算法服务计算历史最优值（不从SQL读取）
        List<BenchmarkHistoryVO> out;
        try {
            out = benchmarkHistoryService.getOptimalValueFromAlgorithm(modelInfoVO, st, et, boundaryValues, marktype);
        } catch (Exception e) {
            log.error("【历史最优值趋势】调用算法服务失败，query={}, marktype={}", query, marktype, e);
            return Result.success(Collections.emptyList());
        }

        // 5) 返回（允许空列表）
        return Result.success(out);
    }

    /**
     * 解析边界参数值字符串
     * @param boundaryValuesStr 逗号分隔的边界参数值，如 "10.5,20.3,30.1"
     * @return Float数组
     */
    private Float[] parseBoundaryValues(String boundaryValuesStr) {
        if (StrUtil.isBlank(boundaryValuesStr)) {
            return new Float[0];
        }
        try {
            String[] parts = boundaryValuesStr.split(",");
            Float[] values = new Float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                values[i] = Float.parseFloat(parts[i].trim());
            }
            return values;
        } catch (NumberFormatException e) {
            log.error("【历史最优值趋势】解析边界参数失败，boundaryValues={}", boundaryValuesStr, e);
            return new Float[0];
        }
    }
}
