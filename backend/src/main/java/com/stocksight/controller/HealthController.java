package com.stocksight.controller;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class HealthController {
    @GetMapping("/actuator/health") public Map<String,String> health() { return Map.of("status","UP"); }
}
