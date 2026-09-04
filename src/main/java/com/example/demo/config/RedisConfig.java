package com.example.demo.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

/**
 * Cấu hình Redis cho phần "nóng" của kì thi.
 *
 * Nguyên tắc: Redis KHÔNG phải nguồn sự thật. MySQL vẫn giữ toàn bộ phiên thi,
 * đáp án và điểm. Redis chỉ giữ ba thứ được phép mất mà không hỏng kì thi:
 *
 *   1. Bản cache đề thi (câu hỏi + lựa chọn) — dựng lại được từ DB.
 *   2. Khoá tạo phiên — mất khoá thì còn UNIQUE(ExamID, StudentID) chốt cuối.
 *   3. Presence heartbeat — mất thì quay lại so cột LastActiveAt như trước.
 *
 * Vì thế toàn bộ code gọi Redis đều bọc try/catch và có đường lui về MySQL:
 * Redis chết là hệ thống chậm đi, không phải là kì thi dừng.
 *
 * Hai template được khai riêng vì hai nhu cầu khác nhau:
 * - {@link StringRedisTemplate} cho khoá và presence: giá trị chỉ là chuỗi ngắn,
 *   cần SET NX/EX nguyên tử, không muốn dính lớp serializer JSON.
 * - {@link RedisTemplate} JSON cho cache đề thi: cần cất cả object DTO.
 */
@Configuration
public class RedisConfig {

    /**
     * Serializer JSON cho các DTO cache.
     *
     * Hai chỗ dễ vấp nếu để mặc định:
     * - {@link JavaTimeModule}: thiếu nó là LocalDateTime ném lỗi ngay khi ghi.
     * - Default typing: cache là {@code List<ExamQuestionView>}, không có thông
     *   tin kiểu nhúng trong JSON thì lúc đọc lại Jackson trả về LinkedHashMap
     *   và code nổ ClassCastException.
     */
    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Ghi ngày dạng ISO-8601 để giá trị trong Redis còn đọc được bằng mắt.
        mapper.disable(WRITE_DATES_AS_TIMESTAMPS);
        // DTO dùng Lombok @Data nên đọc field trực tiếp là đủ và ổn định nhất.
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /** Template cho cache đề thi: key là chuỗi, value là JSON của DTO. */
    @Bean
    public RedisTemplate<String, Object> examRedisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer serializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Key phải là StringRedisSerializer, nếu không thì key trong redis-cli
        // hiện ra dạng nhị phân của JDK serializer và không debug được.
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
