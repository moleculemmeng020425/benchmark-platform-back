package seu.powersis.alert.dao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import seu.powersis.alert.dao.entity.BenchmarkHistory;
import seu.powersis.alert.param.BenchmarkHistoryQuery;
import seu.powersis.alert.vo.BenchmarkHistoryVO;
import seu.powersis.alert.vo.ModelInfoVO;

import java.util.List;

/**
 * @author 10929
 * @description 针对表【benchmark_history】的数据库操作Service
 * @createDate 2024-07-16 16:32:00
 */
public interface BenchmarkHistoryService extends IService<BenchmarkHistory> {
    List<BenchmarkHistoryVO> getHistory(BenchmarkHistoryQuery query);

    /**
     * 根据类型获取历史最优值
     * @param query 查询参数
     * @param type 数据类型（min/max/avg），对应模型的 marktype
     * @return 历史最优值列表
     */
    List<BenchmarkHistoryVO> getHistoryByType(BenchmarkHistoryQuery query, String type);

    /**
     * 调用算法服务计算历史最优值（动态计算，不从SQL读取）
     * @param modelInfoVO 模型信息
     * @param st 开始时间
     * @param et 结束时间
     * @param boundaryValues 边界参数值数组
     * @param type 类型（min/max）
     * @return 历史最优值列表
     */
    List<BenchmarkHistoryVO> getOptimalValueFromAlgorithm(ModelInfoVO modelInfoVO, String st, String et, Float[] boundaryValues, String type);
}
