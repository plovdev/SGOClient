package org.plovdev.sgoclient.core;

import okhttp3.*;
import org.jspecify.annotations.NonNull;
import org.plovdev.sgoclient.core.dto.*;
import org.plovdev.sgoclient.exceptions.SGOCleintException;
import org.plovdev.sgoclient.exceptions.SGORequestException;
import org.plovdev.sgoclient.core.http.CookieStore;
import org.plovdev.sgoclient.core.http.HttpMethod;
import org.plovdev.sgoclient.core.http.SGOHttpPath;
import org.plovdev.sgoclient.reports.requests.LoadSGOReportRequest;
import org.plovdev.sgoclient.core.http.requests.SGORequest;
import org.plovdev.sgoclient.core.http.requests.commons.GetSGOContext;
import org.plovdev.sgoclient.core.http.requests.login.GetSGOLoginData;
import org.plovdev.sgoclient.core.http.requests.login.SGOLoginRequest;
import org.plovdev.sgoclient.core.http.requests.login.SGOLogoutRequest;
import org.plovdev.sgoclient.reports.dto.SGOReport;
import org.plovdev.sgoclient.core.security.AuthKeys;
import org.plovdev.sgoclient.core.security.HashUtils;
import org.plovdev.sgoclient.core.utils.Globals;
import org.plovdev.sgoclient.core.utils.SGOSessionRefresher;
import org.plovdev.sgoclient.core.ws.SGOWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import static org.plovdev.sgoclient.core.utils.Globals.GSON;

public class SGOClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SGOClient.class);
    private static final String DEFAULT_HOST = "sgo.volganet";

    private final CookieStore cookieStore = new CookieStore();
    private final SGOSessionRefresher refresher = new SGOSessionRefresher(this);
    private final String baseHost;
    private OkHttpClient HTTP_CLIENT = Globals.HTTP_CLIENT_BUILDER.cookieJar(cookieStore).build();

    private volatile SGOSession currentSession;

    public SGOClient() {
        this(DEFAULT_HOST);
    }

    public SGOClient(String host) {
        this.baseHost = host;
    }

    public SGOSession getCurrentSession() {
        return currentSession;
    }

    public String getBaseHost() {
        return baseHost;
    }

    public SGOSession createSession(AuthKeys keys, SGOSchool school) {
        return createSession(keys, school, ClientRole.STUDENT);
    }

    public synchronized SGOSession createSession(AuthKeys authKeys, SGOSchool school, ClientRole role) {
        if (currentSession != null) {
            return currentSession;
        }
        if (authKeys == null) {
            throw new IllegalArgumentException("Auth keys can't be null!");
        }
        SGOLoginData loginData = execute(new GetSGOLoginData());
        SGOLoginRequest sgoLoginRequest = new SGOLoginRequest(role.getRole(), authKeys.username(), HashUtils.createPassword(loginData.getSalt(), authKeys.password()), loginData.getLt(), loginData.getVer(), school);
        SGOLogin sgoLogin = execute(sgoLoginRequest);

        currentSession = new SGOSession(loginData, sgoLogin, null);
        SGOContext sgoContext = execute(new GetSGOContext());
        currentSession.setSgoContext(sgoContext);
        refresher.startRefreshLoop(authKeys, school, role);

        return currentSession;
    }

    public <T> T execute(SGORequest<?> request) {
        try (Response response = HTTP_CLIENT.newCall(buildRequest(request)).execute()) {
            String body = response.body().string();

            if (response.isSuccessful()) {
                return GSON.fromJson(body, request.responseType().getType());
            } else {
                throw new SGORequestException("HTTP " + response.code() + ": " + body);
            }
        } catch (IOException e) {
            throw new SGOCleintException(e);
        }
    }

    public SGOReport loadReport(LoadSGOReportRequest request) {
        try (Response response = HTTP_CLIENT.newCall(buildRequest(request)).execute()) {
            ResponseBody body = response.body();
            if (response.isSuccessful()) {
                return new SGOReport(body.bytes());
            } else {
                throw new SGORequestException("HTTP " + response.code() + ": " + body.string());
            }
        } catch (IOException e) {
            throw new SGOCleintException(e);
        }
    }

    private @NonNull Request buildRequest(@NonNull SGORequest<?> request) {
        String sgoHost = String.format(SGOHttpPath.BASE_HOST, baseHost);

        Request.Builder builder = new Request.Builder();
        builder.url(sgoHost + request.endpoint());

        Map<String, String> defHeaders = SGORequest.getDefaultHeaders();
        defHeaders.put("Content-Type", request.contentType());
        addHeaders(builder, defHeaders);
        addHeaders(builder, request.headers());

        if (currentSession != null) {
            SGOLogin sgoLogin = currentSession.getSgoLogin();
            if (sgoLogin != null) {
                builder.header("At", sgoLogin.getAt());
            }
        }

        if (request.method() == HttpMethod.GET) {
            String params = request.params();
            if (params != null && !params.trim().isEmpty()) {
                builder.url(sgoHost + request.endpoint() + "?" + params);
            }
            return builder.get().build();
        } else if (request.method() == HttpMethod.POST) {
            String body = request.params();
            if (body == null || body.trim().isEmpty()) {
                return builder.post(RequestBody.EMPTY).build();
            } else {
                return builder.post(RequestBody.create(body, MediaType.parse(request.contentType()))).build();
            }
        } else {
            throw new UnsupportedOperationException("Unsupported method: " + request.method());
        }
    }

    private void addHeaders(Request.Builder builder, @NonNull Map<String, String> headers) {
        for (String header : headers.keySet()) {
            builder.header(header, headers.get(header));
        }
    }

    public synchronized void setProxy(Proxy proxy, Authenticator authenticator) {
        OkHttpClient.Builder builder = HTTP_CLIENT.newBuilder();
        builder.proxy(Objects.requireNonNullElse(proxy, Proxy.NO_PROXY));
        if (authenticator != null) {
            builder.authenticator(authenticator);
        }
        HTTP_CLIENT = builder.build();
    }

    public SGOWebSocketClient createSGOWebSocketClient() {
        return new SGOWebSocketClient(HTTP_CLIENT);
    }

    @Override
    public synchronized void close() {
        refresher.stopRefreshLoop();
        execute(new SGOLogoutRequest(currentSession.getSgoLogin().getAt(), currentSession.getLoginData().getVer()));
        currentSession = null;
        cookieStore.clearCookie();

        try (ExecutorService service = HTTP_CLIENT.dispatcher().executorService()) {
            service.shutdownNow();
            HTTP_CLIENT.connectionPool().evictAll();
        } catch (Exception e) {
            log.error("SGOClient shutdown error: ", e);
        }

        log.debug("Client closed");
    }
}