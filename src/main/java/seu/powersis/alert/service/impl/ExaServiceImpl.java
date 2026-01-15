package seu.powersis.alert.service.impl;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import seu.powersis.alert.common.model.ExaPoint;
import seu.powersis.alert.service.ExaService;
import seu.powersis.alert.vo.PointOptionItemVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


/**
 * @author chenjiale
 * @version 1.0
 * @date 2023-08-20 20:23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExaServiceImpl implements ExaService {

    private static final String EXA_SEARCH_FORMATTER =
            "ItemName like'%%%s%%' or Descriptor like '%%%s%%'";

    private static final String EXA_SEARCH_KEY = "WhereClause";

    private static final String ID_SPLIT = "|";

    // HTTP请求超时配置（毫秒）
    private static final int CONNECTION_TIMEOUT = 5000;  // 连接超时5秒
    private static final int READ_TIMEOUT = 30000;       // 读取超时30秒


    @Value("${exa.get-item-url}")
    private String getItemsUrl;

    @Value("${exa.get-values}")
    private String getValuesUrl;

    @Value("${exa.get-raw-array-float}")
    private String getRawArrayFloatUrl;

    @Value("${exa.get-history}")
    private String getHistoryUrl;
    private Throwable itemName;


    @Override
    public List<PointOptionItemVO> getPointOptionList(String search) {
        try {
            String whereClause = String.format(EXA_SEARCH_FORMATTER, search, search);
            Map<String, Object> params = new HashMap<>(4);
            params.put(EXA_SEARCH_KEY, whereClause);

            String body = HttpUtil.createGet(getItemsUrl)
                    .form(params)
                    .setConnectionTimeout(CONNECTION_TIMEOUT)
                    .setReadTimeout(READ_TIMEOUT)
                    .execute()
                    .body();
            if (!StringUtils.hasLength(body)) {
                log.error("获取exa异常:{}", search);
                return new ArrayList<>();
            }

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            List<ExaPoint> points = objectMapper.readValue(
                    body,
                    new TypeReference<List<ExaPoint>>() {}
            );

            return points.stream()
                    .map(p -> PointOptionItemVO.builder()
                            .id(p.getDescriptor() + ID_SPLIT
                                    + p.getItemName() + ID_SPLIT
                                    + p.getEngUnits() + ID_SPLIT
                                    + p.getUpperLimit() + ID_SPLIT
                                    + p.getLowerLimit())
                            .name(p.getDescriptor() + "  (" + p.getItemName() + ")")
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("获取exa异常:{}", search, e);
            return new ArrayList<>();
        }
    }

    @Override
    public Float[] getValues(List<String> points) {
        Float[] res = new Float[points.size()];

        Map<String, Object> body = new HashMap<>(2);
        body.put("ItemNameArray", points);

        try (HttpResponse response = HttpUtil.createPost(getValuesUrl)
                .body(JSON.toJSONString(body))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .execute()) {

            String post = response.body();
            JSONObject bodyObj = JSON.parseObject(post);
            JSONArray values = bodyObj.getJSONArray("ValueArray");

            if (values == null || values.size() != points.size()) {
                log.error("exa返回结果异常,points:{},返回结果:{}", points, post);
                return res;
            }

            for (int i = 0; i < values.size(); i++) {
                res[i] = values.getFloatValue(i);
            }

        } catch (Exception e) {
            log.error("获取exa点号时实值异常,points:{}", points, e);
        }

        return res;
    }

    @Override
    public List<Map<String, Object>> getHistory(
            String targetPoint,
            List<String> boundaryPoints,
            String st,
            String et,
            Integer stepSeconds
    ) {
        if (targetPoint == null || targetPoint.trim().isEmpty()) {
            return Collections.emptyList();
        }

        long startMs = toEpochMillis(st);
        long endMs = toEpochMillis(et);

        Map<String, Object> payload = new HashMap<>(4);
        payload.put("ItemName", targetPoint);
        payload.put("StartTime", startMs);   // 毫秒时间戳
        payload.put("EndTime", endMs);       // 毫秒时间戳

        log.info("【EXA请求】URL={}, Body={}", getHistoryUrl, JSON.toJSONString(payload));

        try (HttpResponse resp = HttpUtil.createPost(getHistoryUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(JSON.toJSONString(payload))
                .execute()) {

            String body = resp.body();

            log.info("【EXA响应】Body={}", body);

            if (!StringUtils.hasLength(body)) return new ArrayList<>();

            JSONArray valueArr = null;
            JSONArray stampArr = null;
            try {
                JSONObject obj = JSON.parseObject(body);
                // 获取值数组
                if (obj.containsKey("ItemValueArray")) {
                    valueArr = obj.getJSONArray("ItemValueArray");
                }
                // 获取时间戳数组
                if (obj.containsKey("ItemStampArray")) {
                    stampArr = obj.getJSONArray("ItemStampArray");
                }
            } catch (Exception e) {
                log.error("【EXA解析异常】", e);
            }

            if (valueArr == null || valueArr.isEmpty()) {
                log.warn("【EXA警告】ItemValueArray 为空！");
                return new ArrayList<>();
            }

            // --- 数据组装逻辑 ---
            List<Map<String, Object>> out = new ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            ZoneId zone = ZoneId.systemDefault();

            for (int i = 0; i < valueArr.size(); i++) {
                // 使用 EXA 返回的时间戳（毫秒）
                long tms;
                if (stampArr != null && i < stampArr.size()) {
                    tms = stampArr.getLongValue(i);
                } else {
                    // 兜底：如果没有时间戳数组，使用步长推算
                    int step = (stepSeconds == null || stepSeconds <= 0) ? 60 : stepSeconds;
                    tms = startMs + i * step * 1000L;
                }

                String timeStr = LocalDateTime
                        .ofInstant(java.time.Instant.ofEpochMilli(tms), zone)
                        .format(fmt);

                Map<String, Object> row = new HashMap<>(4);
                row.put("time", timeStr);
                row.put("value", valueArr.getFloatValue(i));
                out.add(row);
            }
            log.info("【EXA成功】成功解析 {} 个点", out.size());
            return out;

        } catch (Exception e) {
            log.error("获取EXA历史值异常", e);
            return new ArrayList<>();
        }
    }

    private static long toEpochMillis(String s) {
        if (s == null) return 0L;
        String t = s.trim();

        // 已经是时间戳（毫秒）
        if (t.matches("^\\d{13,}$")) return Long.parseLong(t);

        // 秒时间戳
        if (t.matches("^\\d{10}$")) return Long.parseLong(t) * 1000L;

        // yyyy-MM-dd HH:mm:ss
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dt = LocalDateTime.parse(t, f);
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
