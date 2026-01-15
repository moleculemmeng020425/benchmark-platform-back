package seu.powersis.alert.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import seu.powersis.alert.common.model.Result;
import seu.powersis.alert.service.ExaService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trend")
public class TrendController {

    private final ExaService exaService;

    /**
     * 历史趋势（目标点+可选边界点）
     * 前端传：targetPoint、boundaryPoints(可选，逗号分隔)、st、et、stepSeconds(可选)
     */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(
            @RequestParam String targetPoint,
            @RequestParam(required = false) String boundaryPoints,
            @RequestParam String st,
            @RequestParam String et,
            @RequestParam(required = false, defaultValue = "60") Integer stepSeconds
    ) {
        List<String> bp = (boundaryPoints == null || boundaryPoints.trim().isEmpty())
                ? Collections.emptyList()
                : Arrays.asList(boundaryPoints.split(","));

        return Result.success(exaService.getHistory(targetPoint, bp, st, et, stepSeconds));
    }
}
