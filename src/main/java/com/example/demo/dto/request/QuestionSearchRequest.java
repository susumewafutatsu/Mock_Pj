package com.example.demo.dto.request;

import com.example.demo.domain.enums.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tham số lọc / tìm kiếm câu hỏi cho GET /api/v1/questions/search.
 * Mọi field đều optional — field nào null/rỗng thì bỏ qua điều kiện tương ứng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSearchRequest {

    /** Cách khớp nhiều tag: ANY = có ít nhất 1 tag, ALL = phải có đủ tất cả tag. */
    public enum TagMode { ANY, ALL }

    /** Có thể truyền nhiều lần: ?tag=dai-so&tag=lop-10 */
    private List<@Size(max = 50) String> tag;

    @Builder.Default
    private TagMode tagMode = TagMode.ANY;

    /** Tìm trong nội dung câu hỏi (LIKE, không phân biệt hoa/thường). */
    @Size(max = 255)
    private String keyword;

    /** topic — lọc theo môn học (Subjects.SubjectID). */
    private Integer subjectId;

    /** category — lọc theo trình độ của môn (SubjectLevels.LevelID). */
    private Integer levelId;

    /** Lọc theo ngân hàng câu hỏi (QuestionBanks.BankID). */
    private Integer bankId;

    @Min(1)
    @Max(5)
    private Integer difficulty;

    private QuestionType questionType;

    private Boolean isAiGenerated;

    /**
     * Chuẩn hoá danh sách tag: trim, lowercase, bỏ rỗng, bỏ trùng, giữ nguyên thứ tự.
     * Trả về set rỗng nếu không có tag nào hợp lệ.
     */
    public Set<String> normalizedTags() {
        Set<String> result = new LinkedHashSet<>();
        if (tag == null) {
            return result;
        }
        for (String raw : tag) {
            if (raw == null) {
                continue;
            }
            String normalized = raw.trim().toLowerCase();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    public TagMode resolvedTagMode() {
        return tagMode == null ? TagMode.ANY : tagMode;
    }

    /** Escape các ký tự đặc biệt của LIKE để '%' hay '_' do user nhập không bị coi là wildcard. */
    public String likeSafeKeyword() {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
