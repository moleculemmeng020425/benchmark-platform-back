package seu.powersis.alert.dao.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import seu.powersis.alert.dao.entity.BenchmarkHistory;
import seu.powersis.alert.dao.service.BenchmarkHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import seu.powersis.alert.dao.mapper.BenchmarkHistoryMapper;
import seu.powersis.alert.param.BenchmarkHistoryQuery;
import seu.powersis.alert.vo.BenchmarkHistoryVO;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
* @author 陈小黑
* @description 针对表【benchmark_history】的数据库操作Service实现
* @createDate 2023-09-12 22:36:41
*/
@Service
public class BenchmarkHistoryServiceImpl extends ServiceImpl<BenchmarkHistoryMapper, BenchmarkHistory>
    implements BenchmarkHistoryService{

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
    public Double getOptimalValueByBId(Integer modelId, String bId, String type) {
        if (modelId == null || bId == null || bId.trim().isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<BenchmarkHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(BenchmarkHistory::getModelId, modelId)
                .eq(BenchmarkHistory::getBId, bId)
                .eq(type != null && !type.isEmpty(), BenchmarkHistory::getType, type)
                .orderByDesc(BenchmarkHistory::getStarttime)  // 取最新的记录
                .last("LIMIT 1");

        BenchmarkHistory record = this.getOne(queryWrapper);
        return record != null ? record.getTargetvalue() : null;
    }
}




