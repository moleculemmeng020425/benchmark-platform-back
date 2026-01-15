package seu.powersis.alert.service;

import seu.powersis.alert.vo.PointOptionItemVO;

import java.util.List;

import java.util.Map;

/**
 * @author chenjiale
 * @version 1.0
 * @date 2023-08-20 20:21
 */
public interface ExaService {
    /**
     * 获取测点选项
     *
     * @param search 查询条件
     * @return 选项
     */
    List<PointOptionItemVO> getPointOptionList(String search);

    public Float[] getValues(List<String> points);
    List<Map<String, Object>> getHistory(
            String targetPoint,
            List<String> boundaryPoints,
            String st,
            String et,
            Integer stepSeconds
    );

    /**
     * 获取单个测点的历史数据
     * @param pointName 测点名称
     * @param st 开始时间
     * @param et 结束时间
     * @param stepSeconds 采样间隔（秒）
     * @return 历史数据列表 [{time, value}, ...]
     */
    List<Map<String, Object>> getSinglePointHistory(
            String pointName,
            String st,
            String et,
            Integer stepSeconds
    );

    /**
     * 获取多个测点的历史数据，按时间对齐
     * @param pointNames 测点名称列表
     * @param st 开始时间
     * @param et 结束时间
     * @param stepSeconds 采样间隔（秒）
     * @return 历史数据列表 [{time, values: [v1, v2, ...]}, ...]
     */
    List<Map<String, Object>> getMultiPointsHistory(
            List<String> pointNames,
            String st,
            String et,
            Integer stepSeconds
    );
}
