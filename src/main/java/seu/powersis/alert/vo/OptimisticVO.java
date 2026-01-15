package seu.powersis.alert.vo;

import lombok.*;

/**
 * @author chenjiale
 * @version 1.0
 * @date 2024-07-16 22:17
 */
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class OptimisticVO {
    private Integer modelId;
    private String targetName;

    private String boundaryName;

    private String targetValue;

    private String boundaryValue;

    private String historyBest;
    
    // 单位
    private String targetUnit;
    private String boundaryUnit;
    
    // 相关参数
    private String relationName;
    private String relationValue;
    private String relationUnit;
}
