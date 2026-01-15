package seu.powersis.alert.controller;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import seu.powersis.alert.common.model.Result;
import seu.powersis.alert.dao.entity.ModelView;
import seu.powersis.alert.dao.service.ModelViewService;
import seu.powersis.alert.param.BenchmarkHistoryQuery;
import seu.powersis.alert.service.ExaService;
import seu.powersis.alert.vo.BenchmarkHistoryVO;
import seu.powersis.alert.vo.ModelInfoVO;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 目标参数历史值趋势（来自 EXA）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exa/history")
class ExaHistoryController {

    private final ModelViewService modelViewService;
    private final ExaService exaService;

    @GetMapping("")
    public Result<List<BenchmarkHistoryVO>> getHistory(BenchmarkHistoryQuery query) {

        ModelView modelView = modelViewService.getById(query.getModelId());
        if (modelView == null || modelView.getModelInfo() == null) {
            return Result.success(Collections.emptyList());
        }

        ModelInfoVO modelInfoVO = JSON.parseObject(modelView.getModelInfo(), ModelInfoVO.class);
        if (modelInfoVO == null || modelInfoVO.getTargetParameter() == null) {
            return Result.success(Collections.emptyList());
        }

        String targetItemName = modelInfoVO.getTargetParameter().getTargetPoint();
        if (targetItemName == null || targetItemName.trim().isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String st = query.getSt() == null ? null : sdf.format(query.getSt());
        String et = query.getEt() == null ? null : sdf.format(query.getEt());

        // stepSeconds=60：每分钟一个点（你也可以改成从前端传）
        List<Map<String, Object>> rows = exaService.getHistory(targetItemName, null, st, et, 60);

        List<BenchmarkHistoryVO> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object timeObj = r.get("time");
            Object vObj = r.get("value");

            Date time = null;
            if (timeObj != null) {
                try {
                    time = sdf.parse(String.valueOf(timeObj));
                } catch (Exception ignore) {}
            }

            Double v = null;
            if (vObj instanceof Number) {
                v = ((Number) vObj).doubleValue();
            } else if (vObj != null) {
                try {
                    v = Double.parseDouble(String.valueOf(vObj));
                } catch (Exception ignore) {}
            }

            out.add(new BenchmarkHistoryVO(time, v));
        }

        return Result.success(out);
    }
}
