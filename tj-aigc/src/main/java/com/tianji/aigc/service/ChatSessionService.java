package com.tianji.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.aigc.entity.ChatSession;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;

import java.util.List;
import java.util.Map;

public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 创建会话session
     *
     * @param num 热门问题的数量
     * @return 会话信息
     */
    SessionVO createSession(Integer num);

    /**
     * 获取热门问题
     *
     * @param num 热门问题的数量
     * @return 热门问题列表
     */
    List<SessionVO.Example> hotExample(Integer num);

    /**
     * 查询单个历史对话详情
     *
     * @param sessionId
     * @return
     */
    List<MessageVO> queryBySessionId(String sessionId);

    /**
     * 大模型回答完成后，根据本轮对话生成标题并更新会话时间
     *
     * @param sessionId 会话ID
     * @param question  本轮用户问题
     * @param answer    本轮大模型完整回答
     * @param userId    用户ID
     */
    void update(String sessionId, String question, String answer, Long userId);

    /**
     * 获取历史会话列表
     *
     * @return 历史会话列表，按时间倒序排列
     */
    Map<String, List<ChatSessionVO>> getHistory();

    /**
     * 删除历史会话
     *
     * @param sessionId 会话ID，用于标识特定的聊天会话
     */
    void deleteHistory(String sessionId);

    /**
     * 更新会话标题
     * @param sessionId
     * @param title
     */
    void updateTitle(String sessionId, String title);
}
