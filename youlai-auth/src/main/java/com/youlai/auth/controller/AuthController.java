package com.youlai.auth.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nimbusds.jose.JWSObject;
import com.youlai.admin.api.dto.UserDTO;
import com.youlai.auth.domain.Oauth2Token;
import com.youlai.common.core.constant.AuthConstants;
import com.youlai.common.core.result.Result;
import com.youlai.common.core.result.ResultCode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.endpoint.TokenEndpoint;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Api(tags = "认证中心")
@RestController
@RequestMapping("/oauth")
@AllArgsConstructor
public class AuthController {

    private TokenEndpoint tokenEndpoint;
    private RedisTemplate redisTemplate;

    @ApiOperation("Oauth2获取token")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "grant_type", paramType = "query", defaultValue = "password", value = "授权模式", required = true),
            @ApiImplicitParam(name = "client_id", paramType = "query", defaultValue = "client", value = "Oauth2客户端ID", required = true),
            @ApiImplicitParam(name = "client_secret", paramType = "query", defaultValue = "123456", value = "Oauth2客户端秘钥", required = true),
            @ApiImplicitParam(name = "refresh_token", paramType = "query", value = "刷新token"),
            @ApiImplicitParam(name = "username", paramType = "query", defaultValue = "admin", value = "登录用户名"),
            @ApiImplicitParam(name = "password", paramType = "query", defaultValue = "123456", value = "登录密码")
    })
    @PostMapping("/token")
    public Result postAccessToken(
            @ApiIgnore Principal principal,
            @ApiIgnore @RequestParam Map<String, String> parameters
    ) throws HttpRequestMethodNotSupportedException {
        OAuth2AccessToken oAuth2AccessToken = tokenEndpoint.postAccessToken(principal, parameters).getBody();
        Oauth2Token oauth2Token = Oauth2Token.builder()
                .token(oAuth2AccessToken.getValue())
                .refreshToken(oAuth2AccessToken.getRefreshToken().getValue())
                .expiresIn(oAuth2AccessToken.getExpiresIn())
                .build();
        return Result.success(oauth2Token);
    }

    @DeleteMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String payload = request.getHeader(AuthConstants.JWT_PAYLOAD_KEY);
        JSONObject jsonObject = JSONUtil.parseObj(payload);

        String jti = jsonObject.getStr("jti"); // JWT唯一标识
        long exp = jsonObject.getLong("exp"); // JWT过期时间戳

        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        if (exp < currentTimeSeconds) { // token已过期
            return Result.custom(ResultCode.TOKEN_INVALID_OR_EXPIRED);
        }
        redisTemplate.opsForValue().set(AuthConstants.TOKEN_BLACKLIST_PREFIX + jti, null, (exp - currentTimeSeconds), TimeUnit.SECONDS);
        return Result.success();
    }
}
