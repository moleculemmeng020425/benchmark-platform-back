package seu.powersis.alert.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author Cascade
 * @version 1.0
 * @date 2025-12-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkHistoryVO {
    private Date time;
    private Double value;
}

