package com.tianji.aigc.constants;

public interface Constant {

    String USER_ID = "userId";
    String REQUEST_ID = "requestId";
    String ID = "id";
    String STOP = "STOP";

    interface Tools {
        String QUERY_COURSE_BY_ID = "根据课程id查询课程详细信息";
    }

    interface ToolParams {
        String COURSE_ID = "课程id";
    }

}