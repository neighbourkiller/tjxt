package com.tianji.aigc.tools;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.api.client.course.CourseClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 课程工具类，提供与课程相关的操作和查询功能。
 */

@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseClient courseClient;
    private static final String FILED_NAME_FORMAT = "{}_{}"; // 用于生成工具结果存储的字段名格式

    @Tool(description = Constant.Tools.QUERY_COURSE_BY_ID)
    public CourseInfo queryCourseById(@ToolParam(description = Constant.ToolParams.COURSE_ID) Long courseId, ToolContext toolContext) {
        // 这里可以调用课程服务的API来获取课程信息
        // 例如：CourseBaseInfoDTO courseBaseInfoDTO = courseService.getCourseBaseInfo(courseId);
        // 然后将其转换为CourseInfo对象返回
        // return CourseInfo.of(courseBaseInfoDTO);

        return Optional.ofNullable(courseId)
                .map(id -> this.courseClient.baseInfo(id, true))
                .map(CourseInfo::of)
                .map(courseInfo -> {
                    // 将课程信息存储在工具上下文中，以便后续使用
                    var requestId = MapUtil.get(toolContext.getContext(), Constant.REQUEST_ID, String.class);
                    var field = String.format(FILED_NAME_FORMAT, StrUtil.lowerFirst(CourseInfo.class.getSimpleName()), courseId);
                    ToolResultHolder.put(requestId, field, courseInfo);
                    return courseInfo;
                })
                .orElse(null);
    }
}
