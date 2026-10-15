package com.greenhouse.backend.ai.tool.web;

import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import com.greenhouse.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Hidden
@RestController
@RequestMapping("/internal/ai/tools")
@RequiredArgsConstructor
public class AgentToolController {
  private final AgentToolGateway gateway;

  @PostMapping("/{name}")
  public ApiResponse<List<?>> execute(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable String name,
      @RequestBody AgentToolGateway.Arguments arguments) {
    return ApiResponse.ok(gateway.execute(authorization, name, arguments));
  }
}
