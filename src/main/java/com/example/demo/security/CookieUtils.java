package com.example.demo.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Optional;

/** Helper đọc/ghi cookie và serialize object vào cookie (dùng cho luồng OAuth2 stateless). */
public final class CookieUtils {

    private CookieUtils() {
    }

    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return Optional.of(cookie);
            }
        }
        return Optional.empty();
    }

    public static void addCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);
    }

    public static void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        getCookie(request, name).ifPresent(existing -> {
            Cookie cookie = new Cookie(name, "");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        });
    }

    public static String serialize(Object object) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
            out.flush();
            return Base64.getUrlEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Không serialize được object vào cookie", e);
        }
    }

    public static <T> T deserialize(Cookie cookie, Class<T> type) {
        byte[] data = Base64.getUrlDecoder().decode(cookie.getValue());
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return type.cast(in.readObject());
        } catch (Exception e) {
            throw new IllegalStateException("Không deserialize được cookie " + cookie.getName(), e);
        }
    }
}
