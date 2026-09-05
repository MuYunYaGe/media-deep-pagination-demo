package com.example.mediapagination.infrastructure.redis;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class MediaIdCodec {

    private static final int MEMBER_LENGTH = 20;

    public String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("media id must be positive");
        }
        return String.format(Locale.ROOT, "%020d", id);
    }

    public long decode(String member) {
        if (member == null || member.length() != MEMBER_LENGTH) {
            throw new IllegalArgumentException("media member must contain exactly 20 digits");
        }
        for (int index = 0; index < member.length(); index++) {
            char digit = member.charAt(index);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("media member must contain exactly 20 digits");
            }
        }
        try {
            long id = Long.parseLong(member);
            if (id <= 0) {
                throw new IllegalArgumentException("decoded media id must be positive");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("media member is outside the long range", exception);
        }
    }
}
