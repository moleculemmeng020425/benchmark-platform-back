package seu.powersis.alert.dao.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import seu.powersis.alert.dao.entity.ModelView;
import seu.powersis.alert.dao.mapper.ModelViewMapper;
import seu.powersis.alert.dao.service.BenchmarkHistoryService;
import seu.powersis.alert.dao.service.ModelViewService;
import seu.powersis.alert.service.ExaService;
import seu.powersis.alert.vo.ModelInfoVO;
import seu.powersis.alert.vo.OptimisticVO;
import seu.powersis.alert.vo.PointVO;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 陈小黑
 * @description 针对表【MODEL_VIEW】的数据库操作Service实现
 * @createDate 2024-07-16 22:16:11
 */
@Service
@RequiredArgsConstructor
public class ModelViewServiceImpl extends ServiceImpl<ModelViewMapper, ModelView>
        implements ModelViewService {
    private final BenchmarkHistoryService benchmarkHistoryService;
    private final ExaService exaService;

    private static final int CONNECTION_TIMEOUT = 5000;  // 连接超时5秒
    private static final int READ_TIMEOUT = 60000;       // 读取超时60秒
    @Value("${algorithm.host}")
    private String algorithmHost;

    @Override
    public List<OptimisticVO> getOptimistic(Integer id, String search, String st, String et) {
        LambdaQueryWrapper<ModelView> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(id != null, ModelView::getSystemId, id);
        List<ModelView> list = list(queryWrapper);
        List<OptimisticVO> result = new ArrayList<>();

        for (ModelView modelView : list) {
            String modelInfo = modelView.getModelInfo();
            if (StrUtil.isBlank(modelInfo)) {
                continue;
            }
            ModelInfoVO modelInfoVO = JSON.parseObject(modelInfo, ModelInfoVO.class);
            OptimisticVO vo = new OptimisticVO();
            String description = modelInfoVO.getTargetParameter().getDescription();
            List<String> boundary = modelInfoVO.getBoundaryParameter().stream()
                    .map(PointVO::getDescription).collect(Collectors.toList());
            if (StrUtil.isNotBlank(search) && !description.contains(search) && !JSON.toJSONString(boundary).contains(search)) {
                continue;
            }
            List<String> boundaryPoints = modelInfoVO.getBoundaryParameter().stream()
                    .map(PointVO::getTargetPoint).collect(Collectors.toList());
            String targetPoint = modelInfoVO.getTargetParameter().getTargetPoint();
            Float[] bValue = exaService.getValues(boundaryPoints);
            List<Float> values = Arrays.stream(bValue)
                    .map(t -> {
                        if (t == null) {
                            return -999F;
                        } else {
                            return Float.parseFloat(NumberUtil.decimalFormat("#.##", t));
                        }
                    }).
                    collect(Collectors.toList());
            Float targetValue = exaService.getValues(ListUtil.of(targetPoint))[0];
            String t;
            if (targetValue == null) {
                t = "-999";
            } else {
                t = NumberUtil.decimalFormat("#.##", targetValue);
            }
            vo.setModelId(modelView.getModelId());
            vo.setTargetName(description);
            vo.setBoundaryName(JSON.toJSONString(boundary));
            vo.setTargetValue(t);
            vo.setBoundaryValue(JSON.toJSONString(values));
            vo.setHistoryBest(getBest(modelInfoVO, st, et, bValue, modelInfoVO.getTargetParameter().getMarktype()));
            
            // 添加单位
            vo.setTargetUnit(modelInfoVO.getTargetParameter().getUnit());
            List<String> boundaryUnits = modelInfoVO.getBoundaryParameter().stream()
                    .map(PointVO::getUnit).collect(Collectors.toList());
            vo.setBoundaryUnit(JSON.toJSONString(boundaryUnits));
            
            // 添加相关参数
            List<PointVO> relationParams = modelInfoVO.getRelationParameter();
            if (relationParams != null && !relationParams.isEmpty()) {
                List<String> relationNames = relationParams.stream()
                        .map(PointVO::getDescription).collect(Collectors.toList());
                List<String> relationPoints = relationParams.stream()
                        .map(PointVO::getTargetPoint).collect(Collectors.toList());
                List<String> relationUnits = relationParams.stream()
                        .map(PointVO::getUnit).collect(Collectors.toList());
                Float[] rValue = exaService.getValues(relationPoints);
                List<Float> relationValues = Arrays.stream(rValue)
                        .map(r -> {
                            if (r == null) {
                                return -999F;
                            } else {
                                return Float.parseFloat(NumberUtil.decimalFormat("#.##", r));
                            }
                        }).collect(Collectors.toList());
                vo.setRelationName(JSON.toJSONString(relationNames));
                vo.setRelationValue(JSON.toJSONString(relationValues));
                vo.setRelationUnit(JSON.toJSONString(relationUnits));
            }
            
            result.add(vo);
        }
        return result;
    }

    public String getBest(ModelInfoVO modelInfoVO, String st, String et, Float[] bValue, String type) {
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
        body.put("object_value", Collections.singletonList(Arrays.asList(bValue)));
        body.put("alg_name", "MLP");
        body.put("type", StrUtil.isBlank(type) ? "max" : type);
        //以下是调用后端python算法
        try (HttpResponse response = HttpUtil.createPost(algorithmHost + "/benchmark")
                .body(JSON.toJSONString(body))
                .setConnectionTimeout(CONNECTION_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .execute()) {
            String responseBody = response.body();
            JSONObject jsonObject = JSON.parseObject(responseBody);
            if (jsonObject == null || !jsonObject.containsKey("result_list")) {
                return JSON.toJSONString(body);
            }
            return jsonObject.getString("result_list");
        } catch (Exception e) {
            log.error("获取最优值异常", e);
            return e.getMessage();
        }

    }
}




