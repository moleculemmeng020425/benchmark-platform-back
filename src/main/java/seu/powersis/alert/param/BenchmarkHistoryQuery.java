package seu.powersis.alert.param;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

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
}

