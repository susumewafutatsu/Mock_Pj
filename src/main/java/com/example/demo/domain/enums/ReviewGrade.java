package com.example.demo.domain.enums;

/** Người học tự đánh giá mình nhớ tới đâu sau khi lật thẻ. */
public enum ReviewGrade {

    /** Không nhớ gì. Thẻ quay lại từ đầu và được xếp ôn lại ngay hôm nay. */
    AGAIN(0),

    /** Nhớ ra nhưng chật vật. Vẫn tính là đúng, chỉ giãn cách chậm hơn. */
    HARD(3),

    /** Nhớ bình thường — mức mặc định. */
    GOOD(4),

    /** Nhớ ngay lập tức. Giãn cách nới rộng nhanh hơn. */
    EASY(5);

    private final int quality;

    ReviewGrade(int quality) {
        this.quality = quality;
    }

    /** Điểm chất lượng theo thang 0–5 của SM-2. */
    public int quality() {
        return quality;
    }

    /** Dưới 3 điểm là quên: SM-2 coi đây là mốc phải học lại từ đầu. */
    public boolean isForgotten() {
        return quality < 3;
    }
}
