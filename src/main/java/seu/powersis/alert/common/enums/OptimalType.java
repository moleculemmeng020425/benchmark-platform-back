package seu.powersis.alert.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 寻优逻辑类型枚举
 *
 * @author chenjiale
 * @version 1.0
 * @date 2023-08-20
 */
@Getter
@AllArgsConstructor
public enum OptimalType {
    /**
     * 寻找最小值作为最优值
     * 适用于：煤耗、能耗、损耗等越小越好的指标
     */
    MIN("min", "最小值最优"),

    /**
     * 寻找最大值作为最优值
     * 适用于：效率、产出等越大越好的指标
     */
    MAX("max", "最大值最优");

    /**
     * 类型代码
     */
    private final String code;

    /**
     * 类型描述
     */
    private final String description;

    /**
     * 根据code获取枚举
     */
    public static OptimalType of(String code) {
        for (OptimalType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return MIN; // 默认返回最小值
    }
}
