package com.gymlog.system;

import com.gymlog.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统级接口。
 *
 * <p>目前只有健康检查。部署后负载均衡器会定期探这个接口来判断服务是否存活。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /**
     * 健康检查。
     *
     * <p>用途：确认服务活着、能响应。这是最轻量的接口——
     * 不查数据库、不查缓存，只证明「Web 层能处理请求」。
     * 如果要探「依赖是否正常」，那是另一个接口（readiness probe），不要混在一起。
     */
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "service", "gym-log-server",
                "status", "up"
        ));
    }
}
