package seu.powersis.alert.param;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

/**
 * @author Cascade
 * @version 1.0
 * @date 2025-12-24
 */
@Data
public class BenchmarkHistoryQuery {
    private Integer modelId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date st;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date et;

    /**
     * 边界参数值列表，以逗号分隔的字符串形式传入
     * 例如："10.5,20.3,30.1"
     */
    private String boundaryValues;
}

