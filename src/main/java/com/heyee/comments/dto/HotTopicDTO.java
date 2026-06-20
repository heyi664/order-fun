package com.heyee.comments.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HotTopicDTO {
    private Long id;
    private String name;
    private Integer rank;
    private Long heat;
    private Integer blogCount;
}

