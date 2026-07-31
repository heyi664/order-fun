package com.heyee.comments.service;

import com.heyee.comments.dto.Result;

public interface IBlogFavoriteService {
    Result toggleFavorite(Long blogId);
    Result queryMyFavorites(Integer current);
    Result queryFavoriteStatus(Long blogId);
}
