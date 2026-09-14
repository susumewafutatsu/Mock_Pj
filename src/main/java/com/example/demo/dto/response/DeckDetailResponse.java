package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Trang chi tiết bộ thẻ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeckDetailResponse {

    private DeckResponse deck;
    private List<DeckCardView> cards;
}
