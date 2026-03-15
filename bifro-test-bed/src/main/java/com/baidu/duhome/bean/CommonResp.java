package com.baidu.duhome.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommonResp {

    private int code;
    private String msg;

    public static CommonResp success() {
        CommonResp resp = new CommonResp();
        resp.code = 0;
        return resp;
    }

    public static CommonResp error(String msg) {
        CommonResp resp = new CommonResp();
        resp.code = -1;
        resp.msg = msg;
        return resp;
    }

}
