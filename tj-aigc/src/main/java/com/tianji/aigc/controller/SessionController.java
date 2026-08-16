package com.tianji.aigc.controller;

import com.tianji.aigc.dto.ChatDTO;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 新建会话
     */
    @PostMapping
    public SessionVO createSession(@RequestParam(value = "n", defaultValue = "3") Integer num) {
        return this.chatSessionService.createSession(num);
    }

    /**
     * 获取热门问题
     * @param num
     * @return
     */
    @GetMapping("/hot")
    public List<SessionVO.Example> getHotQuestion(@RequestParam(value = "n", defaultValue = "3") Integer num) {
        return this.chatSessionService.hotExample(num);
    }

    /**
     * 查询单个历史对话详情
     * @param sessionId
     * @return
     */
    @GetMapping("/{sessionId}")
    public List<MessageVO> queryBySessionId(@PathVariable("sessionId") String sessionId) {
        return chatSessionService.queryBySessionId(sessionId);
    }

    /**
     * 获取历史会话列表
     * @return
     */
    @GetMapping("/history")
    public Map<String, List<ChatSessionVO>> getHistory(){
        return chatSessionService.getHistory();
    }

    /**
     * 删除历史对话
     */
    @DeleteMapping("/history")
    public void deleteHistory(@RequestParam("sessionId") String sessionId) {
        chatSessionService.deleteHistory(sessionId);
    }

    @PutMapping("/history")
    public void update(@RequestParam("sessionId") String sessionId,
                       @RequestParam("title") String title){
        chatSessionService.updateTitle(sessionId, title);
    }
}