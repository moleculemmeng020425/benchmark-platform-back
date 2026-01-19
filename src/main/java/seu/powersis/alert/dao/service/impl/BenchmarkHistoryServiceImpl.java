package seu.powersis.alert.dao.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import seu.powersis.alert.dao.entity.BenchmarkHistory;
import seu.powersis.alert.dao.service.BenchmarkHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import seu.powersis.alert.dao.mapper.BenchmarkHistoryMapper;
import seu.powersis.alert.param.BenchmarkHistoryQuery;
import seu.powersis.alert.vo.BenchmarkHistoryVO;
import seu.powersis.alert.vo.ModelInfoVO;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
* @author 陈小黑
* @description 针对表【benchmark_history】的数据库操作Service实现
* @createDate 2023-09-12 22:36:41
*/
@Slf4j
@Service
public class BenchmarkHistoryServiceImpl extends ServiceImpl<BenchmarkHistoryMapper, BenchmarkHistory>
    implements BenchmarkHistoryService{

    private static final int CONNECTION_TIMEOUT = 5000;  // 连接超时5秒
    private static final int READ_TIMEOUT = 60000;       // 读取超时60秒

    @Value("${algorithm.host}")
    private String algorithmHost;

    @Override
    public List<BenchmarkHistoryVO> getHistory(BenchmarkHistoryQuery query) {
        LambdaQueryWrapper<BenchmarkHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BenchmarkHistory::getModelId, query.getModelId())
                .ge(query.getSt() != null, BenchmarkHistory::getStarttime, query.getSt())
                .le(query.getEt() != null, BenchmarkHistory::getStarttime, query.getEt())
                .orderByAsc(BenchmarkHistory::getStarttime);

        List<BenchmarkHistory> list = this.list(queryWrapper);

        return list.stream()
                .map(item -> new BenchmarkHistoryVO(item.getStarttime(), item.getTargetvalue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<BenchmarkHistoryVO> getHistoryByType(BenchmarkHistoryQuery query, String type) {
        LambdaQueryWrapper<BenchmarkHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BenchmarkHistory::getModelId, query.getModelId())
                .ge(query.getSt() != null, BenchmarkHistory::getStarttime, query.getSt())
                .le(query.getEt() != null, BenchmarkHistory::getStarttime, query.getEt())
                // 关键：根据 type 筛选数据（min/max/avg）
                .likeRight(type != null && !type.isEmpty(), BenchmarkHistory::getType, type)
                .orderByAsc(BenchmarkHistory::getStarttime);

        List<BenchmarkHistory> list = this.list(queryWrapper);

        return list.stream()
                .map(item -> new BenchmarkHistoryVO(item.getStarttime(), item.getTargetvalue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<BenchmarkHistoryVO> getOptimalValueFromAlgorithm(ModelInfoVO modelInfoVO, String st, String et, Float[] boundaryValues, String type) {
        // 构建请求体
        Map<String, Object> modelInfo = new HashMap<>();
        modelInfo.put("id", modelInfoVO.getId());
        List<Map<String, Object>> boundaryParameter = Optional.ofNullable(modelInfoVO.getBoundaryParameter())
                .orElse(Collections.emptyList())
                .stream()
                .map(pointVO -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("lowerlimit", pointVO.getLowerlimit());
                    map.put("upperlimit", pointVO.getUpperlimit());
                    map.put("gridNumber", pointVO.getGridNumber());
                    return map;
                }).collect(Collectors.toList());
        modelInfo.put("boundary_parameter", boundaryParameter);

        Map<String, Object> body = new HashMap<>();
        body.put("model_info", modelInfo);
        body.put("number", 50);
        body.put("st", st);
        body.put("et", et);
        body.put("object_value", Collections.singletonList(Arrays.asList(boundaryValues)));
        body.put("alg_name", "MLP");
        body.put("type", StrUtil.isBlank(type) ? "max" : type);

        log.info("【动态计算历史最优值】请求算法服务，body={}", JSON.toJSONString(body));

        // 调用算法服务
        try (HttpResponse response = HttpUtil.createPost(algorithmHost + "/benchmark")
                .body(JSON.toJSONString(body))
                .setConnectionTimeout(CONNECTION_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .execute()) {
            String responseBody = response.body();
            log.info("【动态计算历史最优值】算法服务返回，response={}", responseBody);

            JSONObject jsonObject = JSON.parseObject(responseBody);
            if (jsonObject == null || !jsonObject.containsKey("result_list")) {
                log.warn("【动态计算历史最优值】返回数据格式异常");
                return Collections.emptyList();
            }

            // 解析result_list字段，返回历史最优值列表
            return parseResultList(jsonObject.getString("result_list"));
        } catch (Exception e) {
            log.error("【动态计算历史最优值】调用算法服务异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析算法返回的result_list字段，转换为BenchmarkHistoryVO列表
     * 假设返回格式为JSON数组，每个元素包含时间和值
     */
    private List<BenchmarkHistoryVO> parseResultList(String resultListStr) {
        List<BenchmarkHistoryVO> result = new ArrayList<>();
        if (StrUtil.isBlank(resultListStr)) {
            return result;
        }

        try {
            JSONArray resultArray = JSON.parseArray(resultListStr);
            if (resultArray == null || resultArray.isEmpty()) {
                return result;
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (int i = 0; i < resultArray.size(); i++) {
                Object item = resultArray.get(i);
                BenchmarkHistoryVO vo = new BenchmarkHistoryVO();

                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    // 尝试解析时间字段
                    if (obj.containsKey("time")) {
                        try {
                            vo.setTime(dateFormat.parse(obj.getString("time")));
                        } catch (ParseException e) {
                            vo.setTime(new Date());
                        }
                    } else {
                        vo.setTime(new Date());
                    }
                    // 尝试解析值字段
                    if (obj.containsKey("value")) {
                        vo.setValue(obj.getDouble("value"));
                    } else if (obj.containsKey("targetvalue")) {
                        vo.setValue(obj.getDouble("targetvalue"));
                    }
                } else if (item instanceof JSONArray) {
                    // 如果是数组格式 [时间, 值]
                    JSONArray arr = (JSONArray) item;
                    if (arr.size() >= 2) {
                        try {
                            vo.setTime(dateFormat.parse(arr.getString(0)));
                        } catch (ParseException e) {
                            vo.setTime(new Date());
                        }
                        vo.setValue(arr.getDouble(1));
                    }
                } else if (item instanceof Number) {
                    // 如果只有数值，用索引作为时间偏移
                    vo.setTime(new Date(System.currentTimeMillis() + i * 60000L)); // 每分钟一个点
                    vo.setValue(((Number) item).doubleValue());
                }

                result.add(vo);
            }
        } catch (Exception e) {
            log.error("【动态计算历史最优值】解析result_list异常", e);
        }

        return result;
    }
}




