package com.heyee.comments.controller;

import com.heyee.comments.dto.Result;
import com.heyee.comments.service.IBlogFavoriteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/blog/favorites")
public class BlogFavoriteController {
    @Resource private IBlogFavoriteService blogFavoriteService;
    @PutMapping("/{blogId}") public Result toggleFavorite(@PathVariable Long blogId) { return blogFavoriteService.toggleFavorite(blogId); }
    @GetMapping("/{blogId}/status") public Result queryFavoriteStatus(@PathVariable Long blogId) { return blogFavoriteService.queryFavoriteStatus(blogId); }
    @GetMapping("/me") public Result queryMyFavorites(@RequestParam(value = "current", defaultValue = "1") Integer current) { return blogFavoriteService.queryMyFavorites(current); }
}
