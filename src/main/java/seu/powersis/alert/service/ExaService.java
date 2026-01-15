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


}
