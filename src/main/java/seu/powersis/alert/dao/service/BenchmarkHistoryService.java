package seu.powersis.alert.dao.service;

import com.baomidou.mybatisplus.extension.service.IService;
import seu.powersis.alert.dao.entity.BenchmarkHistory;
import seu.powersis.alert.param.BenchmarkHistoryQuery;
import seu.powersis.alert.vo.BenchmarkHistoryVO;

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
}
