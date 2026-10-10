package com.stocksight.controller;
import com.stocksight.service.InventoryChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/ai")
public class ChatController {
    private final InventoryChatService chat;
    public ChatController(InventoryChatService chat) { this.chat=chat; }
    public record ChatRequest(@NotBlank String question) {}
    @PostMapping("/chat") public Map<String,String> ask(@Valid @RequestBody ChatRequest r) { return chat.ask(r.question()); }
}
