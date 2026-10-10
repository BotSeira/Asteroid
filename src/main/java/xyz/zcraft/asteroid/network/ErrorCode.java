package xyz.zcraft.asteroid.network;

import com.google.gson.JsonObject;
import lombok.Getter;

@Getter
public enum ErrorCode {
    NOT_FOUND(1001),

    ILLEGAL_ARGUMENT(2001),
    UNAUTHORIZED(2002),

    FETCH_FAILED(3000);

    private final int code;

    ErrorCode(int i) {
        this.code = i;
    }

    public JsonObject toJson() {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("code", code);
        jsonObject.addProperty("httpStatus", getHttpCode());
        return jsonObject;
    }

    public int getHttpCode() {
        return switch (this) {
            case NOT_FOUND -> 404;
            case UNAUTHORIZED -> 401;
            case ILLEGAL_ARGUMENT -> 400;
            default -> 500;
        };
    }
}
